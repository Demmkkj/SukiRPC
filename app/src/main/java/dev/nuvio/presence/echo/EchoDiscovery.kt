package dev.nuvio.presence

import android.content.Context
import android.net.nsd.*
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address

internal data class EchoPeer(val name:String,val ip:String,val pin:String,val model:String,val version:String,val protocol:Int)
@Suppress("DEPRECATION")
internal fun discover(context:Context)=callbackFlow<List<EchoPeer>> {
    val nsd=context.getSystemService(NsdManager::class.java)
    val main=Handler(Looper.getMainLooper())
    val peers=linkedMapOf<String,EchoPeer>()
    val waiting=linkedMapOf<String,NsdServiceInfo>()
    val present=mutableSetOf<String>()
    var resolving=false
    val closed=java.util.concurrent.atomic.AtomicBoolean(false)
    fun next(){
        if(closed.get()||resolving)return
        val name=waiting.keys.firstOrNull()?:return
        val svc=waiting.remove(name)?:return
        resolving=true
        val resolver=object:NsdManager.ResolveListener{
            override fun onResolveFailed(info:NsdServiceInfo,err:Int){main.post{resolving=false;next()}}
            override fun onServiceResolved(info:NsdServiceInfo){main.post{
                if(!closed.get() && name in present){
                    fun attr(key:String)=info.attributes[key]?.toString(Charsets.UTF_8)?.filter{it.code>=32}?.take(80).orEmpty()
                    val host=info.host
                    if(validHint(attr("pin"),info.port,host is Inet4Address && host.isSiteLocalAddress)){
                        peers[name]=EchoPeer(name.take(80),host.hostAddress.orEmpty(),attr("pin"),attr("model"),attr("version"),attr("protocol").toIntOrNull()?:0)
                        trySend(peers.values.toList())
                    }
                }
                resolving=false;next()
            }}
        }
        try{nsd.resolveService(svc,resolver)}catch(e:RuntimeException){resolving=false;main.post{next()}}
    }
    val listener=object:NsdManager.DiscoveryListener{
        override fun onDiscoveryStarted(type:String){}
        override fun onDiscoveryStopped(type:String){}
        override fun onStartDiscoveryFailed(type:String,err:Int){close()}
        override fun onStopDiscoveryFailed(type:String,err:Int){}
        override fun onServiceFound(info:NsdServiceInfo){main.post{if(!closed.get() && info.serviceType.trimEnd('.')==ECHO_TYPE.trimEnd('.') && present.size<32 && present.add(info.serviceName)){waiting[info.serviceName]=info;next()}}}
        override fun onServiceLost(info:NsdServiceInfo){main.post{present.remove(info.serviceName);waiting.remove(info.serviceName);peers.remove(info.serviceName);trySend(peers.values.toList())}}
    }
    nsd.discoverServices(ECHO_TYPE,NsdManager.PROTOCOL_DNS_SD,listener)
    awaitClose{closed.set(true);runCatching{nsd.stopServiceDiscovery(listener)}}
}
