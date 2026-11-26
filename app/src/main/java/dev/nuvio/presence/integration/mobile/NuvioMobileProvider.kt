package dev.nuvio.presence

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.provider.Settings

internal object NuvioMobileProvider {
    @Volatile var artworkStatus="sem dados"
    private val imdbIdPattern = Regex("^(tt[0-9]{7,12})(?::[0-9]{1,5}:[0-9]{1,5})?$")
    private val artworkUriKeys = arrayOf(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
    private val artworkBitmapKeys = arrayOf(MediaMetadata.METADATA_KEY_DISPLAY_ICON, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART)
    private fun imdbPoster(raw:String):String {
        // Only canonical IMDb title IDs; never derive from filenames or private URLs.
        val id=imdbIdPattern.matchEntire(raw.trim())?.groupValues?.get(1)?:return ""
        return publicArtwork("https://images.metahub.space/poster/medium/$id/img")
    }
    private val packages=setOf("com.nuvio.app","com.nuviodebug.com")
    fun component(c:Context)=ComponentName(c,NuvioMediaListener::class.java)
    fun allowed(c:Context):Boolean = if(Build.VERSION.SDK_INT>=27){
        c.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(c))
    }else{
        Settings.Secure.getString(c.contentResolver,"enabled_notification_listeners").orEmpty().split(':').any{ComponentName.unflattenFromString(it)==component(c)}
    }
    fun accepts(controller:MediaController)=controller.packageName in packages
    fun activeControllers(c:Context):List<MediaController> {
        val manager=c.getSystemService(MediaSessionManager::class.java)
        return manager.getActiveSessions(component(c)).filter{accepts(it)}
    }
    fun readSample(controllers:List<MediaController>,showArtwork:Boolean):Playback? {
        val now=SystemClock.elapsedRealtime()
        val candidates=controllers.mapNotNull { controller ->
            val m=controller.metadata?:return@mapNotNull null
            val ps=controller.playbackState?:return@mapNotNull null
            val state=when(ps.state){
                PlaybackState.STATE_PLAYING->"playing"
                PlaybackState.STATE_PAUSED->"paused"
                PlaybackState.STATE_BUFFERING->"buffering"
                PlaybackState.STATE_CONNECTING->"connecting"
                PlaybackState.STATE_FAST_FORWARDING->"forwarding"
                PlaybackState.STATE_REWINDING->"rewinding"
                else->return@mapNotNull null
            }
            fun text(key:String)=m.getString(key).orEmpty().trim()
            val title=listOf(text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),text(MediaMetadata.METADATA_KEY_TITLE)).firstOrNull{it.isNotBlank()}?:return@mapNotNull null
            val episode=listOf(text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),text(MediaMetadata.METADATA_KEY_ARTIST)).firstOrNull{it.isNotBlank()&&it!=title}.orEmpty()
            // Stop at the first accepted URL. Only inspect description/bitmap if needed.
            var hasRawArtwork = false
            fun checkArtwork(raw: String): String {
                if (raw.isNotBlank()) hasRawArtwork = true
                return publicArtwork(raw)
            }
            var direct = ""
            if(showArtwork) for (key in artworkUriKeys) {
                direct = checkArtwork(text(key))
                if (direct.isNotEmpty()) break
            }
            if (showArtwork && direct.isEmpty()) direct = checkArtwork(m.description.iconUri?.toString().orEmpty())
            val mediaId=text(MediaMetadata.METADATA_KEY_MEDIA_ID)
            val art=if(showArtwork) direct.ifBlank{imdbPoster(mediaId)} else ""
            val info=when {
                !showArtwork->"desativadas nos ajustes"
                direct.isNotEmpty()->"URL publica aceita"
                art.isNotEmpty()->"URL criada a partir do ID IMDb"
                hasRawArtwork->"URI presente, mas nao permitida"
                artworkBitmapKeys.any{key->m.containsKey(key)}->"Chave de Bitmap presente, sem URL publica nem ID IMDb"
                else->"sem URL, Bitmap ou ID IMDb"
            }
            val duration=if(m.containsKey(MediaMetadata.METADATA_KEY_DURATION))m.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf{it>0}?:-1L else -1L
            val speed=ps.playbackSpeed.toDouble().let{if(it.isFinite())it else 0.0}
            var pos=ps.position
            if(pos>=0&&state=="playing"&&speed>0&&ps.lastPositionUpdateTime>0&&ps.lastPositionUpdateTime<=now){pos+=((now-ps.lastPositionUpdateTime)*speed).toLong()}
            if(pos>=0&&duration>0)pos=pos.coerceAtMost(duration)
            Playback(title,episode,"mobile:"+mediaId,art,state,pos,duration,speed,now) to info
        }.filter{it.first.valid}
        val selected=candidates.firstOrNull{it.first.state=="playing"}?:candidates.firstOrNull()
        artworkStatus=selected?.second?:"sem sessao valida"
        return selected?.first
    }
    fun resolvePoster(c:Context,playback:Playback?,showArtwork:Boolean):Playback? {
        playback?:return null
        if(!showArtwork || playback.artwork.isNotEmpty())return playback
        if(!dev.nuvio.presence.Settings.prefs(c).getBoolean("mobilePosterSearch",false)){
            artworkStatus += " | busca externa desativada"
            return playback
        }
        val result=MobilePosterSearch.lookup(playback.title)
        artworkStatus=result.status
        return if(result.url.isEmpty())playback else playback.copy(artwork=result.url)
    }
}
