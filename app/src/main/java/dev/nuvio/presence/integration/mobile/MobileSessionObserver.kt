package dev.nuvio.presence

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

// A single observer per PresenceService. Callbacks invalidate only; all reads happen on IO.
// Keep periodic reconciliation (Discord retries and clock), but skip redundant Binder/metadata reads.
internal class MobileSessionObserver(context:Context) {
    private val context=context.applicationContext
    private val manager=this.context.getSystemService(MediaSessionManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private val dirty=AtomicBoolean(true)
    private val callbacks=LinkedHashMap<MediaSession.Token,Pair<MediaController,MediaController.Callback>>()
    @Volatile private var tracked=emptyList<MediaSession.Token>()
    private var listening=false
    private var healthy=false
    private var closed=false
    private var sampledAt=-1L
    private var artworkEnabled=false
    private var cached:Playback?=null
    private var cachedStatus="sem dados"
    private val listener=MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        // Ignore changes involving only unrelated apps; never inspect their metadata.
        val tokens=sessions.orEmpty().filter{NuvioMobileProvider.accepts(it)}.map{it.sessionToken}
        if(tokens!=tracked)dirty.set(true)
    }

    @Synchronized fun read(showArtwork:Boolean):Playback? {
        if(closed)return null
        if(!NuvioMobileProvider.allowed(context)){
            release()
            NuvioMobileProvider.artworkStatus="sem acesso às notificações"
            return null
        }
        val now=SystemClock.elapsedRealtime()
        val changed=dirty.getAndSet(false)
        if(changed||!healthy||sampledAt<0||now-sampledAt>=10000L||artworkEnabled!=showArtwork){
            try {
                if(!listening){
                    listening=runCatching{
                        manager.addOnActiveSessionsChangedListener(listener,NuvioMobileProvider.component(context),handler)
                    }.isSuccess
                }
                val controllers=NuvioMobileProvider.activeControllers(context)
                val tokens=controllers.map{it.sessionToken}
                tracked=tokens
                val keep=tokens.toSet()
                val iter=callbacks.entries.iterator()
                while(iter.hasNext()){
                    val entry=iter.next()
                    if(entry.key !in keep){
                        runCatching{entry.value.first.unregisterCallback(entry.value.second)}
                        iter.remove()
                    }
                }
                for(controller in controllers){
                    val token=controller.sessionToken
                    if(callbacks.containsKey(token))continue
                    val cb=object:MediaController.Callback(){
                        override fun onMetadataChanged(metadata:MediaMetadata?){dirty.set(true)}
                        override fun onPlaybackStateChanged(state:PlaybackState?){dirty.set(true)}
                        override fun onSessionDestroyed(){dirty.set(true)}
                    }
                    if(runCatching{controller.registerCallback(cb,handler)}.isSuccess)callbacks[token]=controller to cb
                }
                // If callbacks are not available, read normally at every service poll.
                healthy=listening&&tokens.all{callbacks.containsKey(it)}
                cached=NuvioMobileProvider.readSample(controllers,showArtwork)
                cachedStatus=NuvioMobileProvider.artworkStatus
                artworkEnabled=showArtwork
                sampledAt=SystemClock.elapsedRealtime()
            }catch(e:Exception){
                release()
                throw e
            }
        }
        // Rebase from the original sample, never accumulate rounding errors. A fresh sample is
        // forced at 10s; Playback.positionAt's 5s UI extrapolation cap remains unchanged.
        val time=SystemClock.elapsedRealtime()
        val p=cached?.let{sample->
            var pos=sample.position
            if(pos>=0&&sample.state=="playing"&&sample.speed>0){
                pos+=((time-sample.receivedAt).coerceAtLeast(0)*sample.speed).toLong()
            }
            if(pos>=0&&sample.duration>0)pos=pos.coerceAtMost(sample.duration)
            sample.copy(position=pos,receivedAt=time).takeIf{it.valid}
        }
        NuvioMobileProvider.artworkStatus=if(p==null)"sem sessao valida" else cachedStatus
        // Resolve async cover results even if media metadata itself has not changed.
        return NuvioMobileProvider.resolvePoster(context,p,showArtwork)
    }

    private fun release(){
        if(listening)runCatching{manager.removeOnActiveSessionsChangedListener(listener)}
        listening=false
        for((controller,callback) in callbacks.values)runCatching{controller.unregisterCallback(callback)}
        callbacks.clear();tracked=emptyList();healthy=false;cached=null;sampledAt=-1L;dirty.set(true)
    }
    @Synchronized fun close(){if(!closed){closed=true;release()}}
}
