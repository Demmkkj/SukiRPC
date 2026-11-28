package dev.nuvio.presence.ui.eclipse

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.SystemClock
import dev.nuvio.presence.Dashboard
import dev.nuvio.presence.ArtworkImages
import dev.nuvio.presence.ClockDisplay
import dev.nuvio.presence.Hidden
import dev.nuvio.presence.artworkAllowed
import dev.nuvio.presence.episodeAllowed
import dev.nuvio.presence.readPrivacy
import dev.nuvio.presence.readFormat
import dev.nuvio.presence.mediaPresence
import dev.nuvio.presence.MainActivity
import dev.nuvio.presence.Monitor
import dev.nuvio.presence.PresenceService
import dev.nuvio.presence.Settings
import dev.nuvio.presence.timestamp
import dev.nuvio.presence.visibleMediaTitle
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
        val art:String="",val progress:Int?=null,val local:ByteArray?=null)
    private val manager=service.getSystemService(NotificationManager::class.java)
    private val prefs=Settings.prefs(service)
    private var observer:Job?=null
    private var artworkJob:Job?=null
    private var active=false
    private var frame:Frame?=null
    private var bitmap:Bitmap?=null
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
        if(key=="art" || key=="time" || key=="episode" || key?.startsWith("prism.") == true ||
            key?.startsWith("hz.art.") == true) scope.launch { update(Monitor.state.value) }
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
        frame=null;bitmap=null
    }
    private fun snapshot(d:Dashboard):Frame {
        val privacy = readPrivacy(prefs)
        val generic = Frame("SukiRPC", d.connection, d.discord)
        d.studio?.let {
            return if (privacy.hidden.isNotEmpty() || privacy.blocks(it.name)) generic
            else Frame(it.name, it.details.ifBlank { "Rich Presence personalizado" }, d.discord)
        }
        val p=d.playback?.takeIf{it.valid}
        if (p == null || privacy.blocks(p.title)) return generic
        val o=Settings.options(service)
        val format = readFormat(prefs.all)
        val episodeVisible = episodeAllowed(o, privacy)
        val progressVisible = o.timestamps && Hidden.Progress !in privacy.hidden && format.clock != ClockDisplay.Off
        val state=when(p.state){
            "playing"->"Reproduzindo";"paused"->"Pausado"
            "buffering","connecting"->"Carregando"
            "forwarding","rewinding"->"Buscando"
            else->"Reprodução ativa"
        }
        val mono = SystemClock.elapsedRealtime()
        val pos = if (progressVisible) p.positionAt(mono) else -1L
        // Quantize playing progress to 30 s; paused/seek/state changes remain visible.
        val step=if(p.state=="playing" && pos>=0) (pos/30000L)*30000L else pos
        val progress=if(p.duration>0&&step>=0)
            ((step.toDouble()/p.duration)*100).toInt().coerceIn(0,100) else null
        val time=if(step>=0) {
            val prefix=if(p.state=="playing")"≈ " else ""
            prefix+timestamp(step)+(if(p.duration>0)" / "+timestamp(p.duration) else "")
        } else ""
        val detail=listOf(state,if(episodeVisible)p.episode.take(180) else "",time)
            .filter{it.isNotBlank()}.joinToString(" · ")
        val source=d.connection.substringBefore(" • ")
        val discord=if(d.discord=="Presença confirmada")"Discord sincronizado" else d.discord
        val art = mediaPresence(p, o, format, privacy, source, mono, System.currentTimeMillis())?.art.orEmpty()
        val local = p.localArtwork?.takeIf { p.artworkPermitted && artworkAllowed(o, privacy, p.title) }
        return Frame(visibleMediaTitle(p, privacy, episodeVisible).take(180).ifBlank { "SukiRPC" },
            detail, "$source · $discord", art, progress, local)
    }
    private fun update(d:Dashboard){
        if(!active||!d.running)return
        val next=snapshot(d)
        val old = frame
        if (next == old) return
        frame = next
        if (next.art != old?.art || next.local !== old?.local) {
            generation++
            artworkJob?.cancel()
            artworkJob = null
            bitmap = null
            if (next.art.isNotBlank() || next.local != null) loadArtwork(next, generation)
        }
        post()
    }
    private fun post(){
        if(!active)return
        val f=frame?:return
        // A denied notification permission must never interrupt the RPC foreground service.
        try{manager.notify(id,build(f,bitmap))}catch(_:RuntimeException){}
    }
    private fun loadArtwork(next: Frame, token: Long) {
        artworkJob = scope.launch {
            val image = try {
                withContext(Dispatchers.IO) { ArtworkImages.bitmap(service, next.art, next.local, 128, 192) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (active && token == generation && image != null) {
                bitmap = image
                post()
            }
        }
    }
}
