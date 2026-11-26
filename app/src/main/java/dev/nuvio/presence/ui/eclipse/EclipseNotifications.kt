package dev.nuvio.presence.ui.eclipse

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dev.nuvio.presence.Dashboard
import dev.nuvio.presence.MainActivity
import dev.nuvio.presence.Monitor
import dev.nuvio.presence.PresenceService
import dev.nuvio.presence.Settings
import dev.nuvio.presence.publicArtwork
import dev.nuvio.presence.timestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Notification presentation only. RPC cadence, pairing and playback stay independent.
internal class EclipseNotifications(
    private val service:PresenceService,
    private val scope:CoroutineScope,
    private val channel:String,
    private val id:Int,
    private val stopAction:String
){
    private data class Frame(val title:String,val detail:String,val status:String,
        val art:String="",val progress:Int?=null)
    private val manager=service.getSystemService(NotificationManager::class.java)
    private val prefs=Settings.prefs(service)
    private var observer:Job?=null
    private var artworkJob:Job?=null
    private var active=false
    private var frame:Frame?=null
    private var bitmap:Bitmap?=null
    private var currentArt=""
    private var generation=0L
    private val open by lazy {
        PendingIntent.getActivity(service,1,Intent(service,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    private val stop by lazy {
        PendingIntent.getService(service,2,Intent(service,PresenceService::class.java).setAction(stopAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if(key=="art" || key=="time" || key=="episode") scope.launch { update(Monitor.state.value) }
    }
    private fun base()=Notification.Builder(service,channel)
        .setSmallIcon(dev.nuvio.presence.R.drawable.suki_eclipse_notification)
        .setColor(0xffc5a1ff.toInt())
        .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
        .setShowWhen(false).setCategory(Notification.CATEGORY_SERVICE).setLocalOnly(true)
        .addAction(Notification.Action.Builder(null,"Parar sincronização",stop).build())

    private fun build(f:Frame,art:Bitmap?):Notification {
        val public=base().setContentTitle("SukiRPC").setContentText("Sincronização em andamento").build()
        val b=base().setContentTitle(f.title).setContentText(f.detail)
            .setSubText(f.status).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
        if(art!=null)b.setLargeIcon(art)
        f.progress?.let { b.setProgress(100,it,false) }
        // Use the system template to preserve the platform's accessibility, sizing and contrast.
        return b.build()
    }
    fun starting():Notification=build(Frame("SukiRPC","Conectando ao Nuvio","Eclipse"),null)

    fun start(){
        if(active)return
        active=true
        prefs.registerOnSharedPreferenceChangeListener(listener)
        observer=scope.launch { Monitor.state.collect { update(it) } }
    }
    fun stop(){
        if(!active)return
        active=false;generation++
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        observer?.cancel();observer=null;artworkJob?.cancel();artworkJob=null
        frame=null;bitmap=null;currentArt=""
    }
    private fun snapshot(d:Dashboard):Frame {
        val p=d.playback?.takeIf{it.valid}
        if(p==null)return Frame("SukiRPC",d.connection,d.discord)
        val o=Settings.options(service)
        val state=when(p.state){
            "playing"->"Reproduzindo";"paused"->"Pausado"
            "buffering","connecting"->"Carregando"
            "forwarding","rewinding"->"Buscando"
            else->"Reprodução ativa"
        }
        val pos=p.positionAt(SystemClock.elapsedRealtime())
        // Quantize playing progress to 30 s; paused/seek/state changes remain visible.
        val step=if(p.state=="playing" && pos>=0) (pos/30000L)*30000L else pos
        val progress=if(o.timestamps&&p.duration>0&&step>=0)
            ((step.toDouble()/p.duration)*100).toInt().coerceIn(0,100) else null
        val time=if(o.timestamps&&step>=0) {
            val prefix=if(p.state=="playing")"≈ " else ""
            prefix+timestamp(step)+(if(p.duration>0)" / "+timestamp(p.duration) else "")
        } else ""
        val detail=listOf(state,if(o.episode)p.episode.take(180) else "",time)
            .filter{it.isNotBlank()}.joinToString(" · ")
        val source=d.connection.substringBefore(" • ")
        val discord=if(d.discord=="Presença confirmada")"Discord sincronizado" else d.discord
        val art=if(o.artwork)publicArtwork(p.artwork) else ""
        return Frame(p.title.take(180),detail,"$source · $discord",art,progress)
    }
    private fun update(d:Dashboard){
        if(!active||!d.running)return
        val next=snapshot(d)
        if(next==frame)return
        frame=next
        if(next.art!=currentArt){
            generation++;artworkJob?.cancel();artworkJob=null
            currentArt=next.art;bitmap=null
            if(currentArt.isNotBlank())loadArtwork(currentArt,generation)
        }
        post()
    }
    private fun post(){
        if(!active)return
        val f=frame?:return
        // A denied notification permission must never interrupt the RPC foreground service.
        try{manager.notify(id,build(f,bitmap))}catch(_:RuntimeException){}
    }
    private fun loadArtwork(url:String,token:Long){
        artworkJob=scope.launch {
            val image=try{
                withContext(Dispatchers.IO){
                    val request=ImageRequest.Builder(service.applicationContext).data(url)
                        .size(128,192).allowHardware(false).build()
                    val result=service.applicationContext.imageLoader.execute(request)
                    ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){null}
            if(active&&token==generation&&url==currentArt&&image!=null){bitmap=image;post()}
        }
    }
}
