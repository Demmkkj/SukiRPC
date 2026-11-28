package dev.nuvio.presence

import android.content.Context
import android.net.*
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.IOException

internal data class LinkSample(val version:String,val protocol:Int,val latency:Long,val synced:Long)
internal object EchoHealth {
    val links=MutableStateFlow<Map<String,LinkSample>>(emptyMap())
    val failures=MutableStateFlow(0)
    val errors=MutableStateFlow<Map<String,String>>(emptyMap())
    fun error(e:Throwable)=when{
        generateSequence(e){it.cause}.any{it is java.security.cert.CertificateException || it is javax.net.ssl.SSLHandshakeException}->"Certificado divergente ou inválido · valide a identidade na TV"
        e.message=="Pareamento revogado"->"Autenticação recusada · pareie novamente"
        e.message=="Protocolo incompatível"->"Protocolo incompatível · confira a versão do Collector"
        e.message=="Coletor sem dados recentes"->"Sessão desatualizada · abra o Nuvio na TV"
        else->"Collector indisponível · confira a rede e o acesso às notificações"
    }
    fun read(c:Context,d:PairedDevice,art:Boolean=true):JSONObject {
        val start=SystemClock.elapsedRealtime()
        val j=LanClient.playback(d,Integrations(c).ordered(),art)
        val proto=if(j.has("protocol"))j.getInt("protocol") else 1
        check(proto in 1..2 && (proto==1||j.optJSONArray("capabilities")?.let{arr->(0 until arr.length()).any{arr.optString(it)=="snapshot"}}==true)){"Protocolo incompatível"}
        check(protocolOK(proto,j.optLong("sample_age_ms",-1))){"Coletor sem dados recentes"}
        val version=j.optString("collector_version").takeIf{it.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}"""))}?:"Legado"
        val sample=LinkSample(version,proto,SystemClock.elapsedRealtime()-start,System.currentTimeMillis())
        links.update{it+(d.pin to sample)};errors.update{it-d.pin}
        val prefs=Settings.prefs(c)
        if(sample.synced-prefs.getLong("echo.last."+d.pin,0)>60000 || prefs.getString("echo.version."+d.pin,null)!=version)
            prefs.edit().putLong("echo.last."+d.pin,sample.synced).putString("echo.version."+d.pin,version).apply()
        if(prefs.getBoolean("echo.verbose",false))Monitor.event(Event.SAMPLE)
        return j
    }
}
internal class EchoSession(private val context:Context){
    private val cm=context.getSystemService(ConnectivityManager::class.java)
    private val wake=Channel<Unit>(Channel.CONFLATED)
    private val generation=NetworkEpoch()
    private val callback=object:ConnectivityManager.NetworkCallback(){
        override fun onAvailable(network:Network){generation.changed();wake.trySend(Unit)}
        override fun onLost(network:Network){generation.changed();wake.trySend(Unit)}
    }
    private var seen=0
    private var nextDiscovery=0L
    private val cooldown=mutableMapOf<String,Long>()
    private val attempts=mutableMapOf<String,Int>()
    private var cursor=0
    var playingPin:String?=null
    private set
    fun open(){cm.registerDefaultNetworkCallback(callback)}
    fun close(){runCatching{cm.unregisterNetworkCallback(callback)};wake.close()}
    suspend fun waitFor(ms:Long){withTimeoutOrNull(ms){wake.receive()}}
    suspend fun artwork(pin: String, key: String): ByteArray? = runInterruptible(Dispatchers.IO) {
        DeviceStore.get(context, pin)?.let { LanClient.artwork(it, key) }
    }

    suspend fun tv(auto:Boolean,art:Boolean,decode:(JSONObject,Long,Boolean)->Playback?):Pair<String,Playback?>?{
        val epoch=generation.current()
        if(seen!=epoch){seen=epoch;cooldown.clear();nextDiscovery=0;cursor=0;playingPin=null}
        if(cm.activeNetwork==null)throw IOException("Sem rede")
        val selected=withContext(Dispatchers.IO){DeviceStore.load(context)}?:return null
        val ids=routes(selected.pin,if(auto)withContext(Dispatchers.IO){DeviceStore.ids(context)} else emptyList(),auto)
        val now=SystemClock.elapsedRealtime()
        val available=ids.filter{now>=(cooldown[it]?:0)}
        val pin=playingPin?.takeIf{it in available}?:available.getOrNull(if(available.isEmpty())0 else cursor%available.size)?:return null
        val device=withContext(Dispatchers.IO){DeviceStore.get(context,pin)}?:return null
        suspend fun read(d:PairedDevice)=withContext(Dispatchers.IO){EchoHealth.read(context,d,art)}
        try{
            val response=try{read(device)}catch(e:IOException){
                if(now<nextDiscovery)throw e
                nextDiscovery=now+30000
                val peer=withTimeoutOrNull(2200){discover(context).mapNotNull{all->all.firstOrNull{it.pin==pin && it.ip!=device.ip}}.firstOrNull()}?:throw e
                val moved=device.copy(ip=peer.ip)
                read(moved).also{if(generation.accepts(epoch))withContext(Dispatchers.IO){DeviceStore.relocate(context,device,moved)}}
            }
            if(!generation.accepts(epoch))throw IOException("Rede alterada")
            val media=decode(response,SystemClock.elapsedRealtime(),art)
            playingPin=pin.takeIf{media?.state=="playing"}
            cursor=if(playingPin==null)(cursor+1)%ids.size else 0
            cooldown.remove(pin);attempts.remove(pin)
            return pin to media
        }catch(e:CancellationException){throw e}catch(e:Exception){
            playingPin=null
            val n=(attempts[pin]?:0)+1;attempts[pin]=n
            cooldown[pin]=now+retryDelay(n,kotlin.random.Random.nextLong(1000))
            EchoHealth.errors.update{it+(pin to EchoHealth.error(e))};EchoHealth.failures.update{it+1}
            Monitor.event(if(EchoHealth.error(e).startsWith("Certificado")||e.message=="Pareamento revogado")Event.AUTH else if(e.message=="Protocolo incompatível")Event.FORMAT else Event.LOST)
            throw e
        }
    }
}
