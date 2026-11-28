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
import dev.nuvio.artwork.SessionArtwork
import dev.nuvio.artwork.ArtworkPolicy

internal object NuvioMobileProvider {
    fun component(c:Context)=ComponentName(c,NuvioMediaListener::class.java)
    fun allowed(c:Context):Boolean = if(Build.VERSION.SDK_INT>=27){
        c.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(c))
    }else{
        Settings.Secure.getString(c.contentResolver,"enabled_notification_listeners").orEmpty().split(':').any{ComponentName.unflattenFromString(it)==component(c)}
    }
    fun accepts(c:Context,controller:MediaController)=Integrations(c).accepts(controller.packageName)
    fun activeControllers(c:Context):List<MediaController> {
        val manager=c.getSystemService(MediaSessionManager::class.java)
        return manager.getActiveSessions(component(c)).filter{accepts(c,it)}
    }
    private data class Sample(val playback: Playback, val metadata: MediaMetadata)

    fun readSample(
        rules: Integrations, previous: String?, controllers: List<MediaController>,
        showArtwork: Boolean, privacy: MediaPrivacy, bitmapCache: SessionBitmapCache
    ): Playback? {
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
            if(state=="paused"&&rules.clearOnPause(controller.packageName))return@mapNotNull null
            fun text(key:String)=m.getString(key).orEmpty().trim()
            val title=listOf(text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),text(MediaMetadata.METADATA_KEY_TITLE)).firstOrNull{it.isNotBlank()}?:return@mapNotNull null
            val episode=listOf(text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),text(MediaMetadata.METADATA_KEY_ARTIST)).firstOrNull{it.isNotBlank()&&it!=title}.orEmpty()
            val mediaId=text(MediaMetadata.METADATA_KEY_MEDIA_ID)
            val duration=if(m.containsKey(MediaMetadata.METADATA_KEY_DURATION))m.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf{it>0}?:-1L else -1L
            val speed=ps.playbackSpeed.toDouble().let{if(it.isFinite())it else 0.0}
            var pos=ps.position
            if(pos>=0&&state=="playing"&&speed>0&&ps.lastPositionUpdateTime>0&&ps.lastPositionUpdateTime<=now){pos+=((now-ps.lastPositionUpdateTime)*speed).toLong()}
            if(pos>=0&&duration>0)pos=pos.coerceAtMost(duration)
            Sample(Playback(title,episode,"mobile:"+mediaId,"",state,pos,duration,speed,now,
                m.getLong(MediaMetadata.METADATA_KEY_YEAR).takeIf { it in 1000L..9999L }?.toString().orEmpty(),
                text(MediaMetadata.METADATA_KEY_GENRE),controller.packageName), m)
        }.filter{it.playback.valid}
        val selected = Horizon.choose(candidates, { it.playback.appId }, { it.playback.state == "playing" }, rules::rank, previous)
        if (selected == null) {
            bitmapCache.clear()
            return null
        }
        val p = selected.playback
        if (!showArtwork || privacy.blocks(p.title) || !rules.artwork(p.appId)) {
            bitmapCache.clear()
            return p.withoutArtwork()
        }
        val art = SessionArtwork.resolve(selected.metadata, p.appId, p.mediaId.removePrefix("mobile:"))
        val local = if (art.url.isEmpty()) bitmapCache.read(p.identity, selected.metadata)
            else null.also { bitmapCache.clear() }
        val id = p.mediaId.removePrefix("mobile:")
        val idLabel = when {
            id.isEmpty() -> "ID ausente"
            ArtworkPolicy.fromMediaId(id, p.appId).isNotEmpty() -> "ID reconhecido"
            else -> "ID opaco"
        }
        val signals = listOf(idLabel,
            if (selected.metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).isNullOrBlank()) "URI de mídia ausente" else "URI de mídia presente",
            if (SessionArtwork.hasBitmap(selected.metadata)) "Campo de bitmap presente" else "Campo de bitmap ausente")
        return p.copy(artwork = art.url, artworkSource = art.source, artworkRejected = art.rejected,
            localArtwork = local, artworkSignals = signals.joinToString(" · "))
    }
}
