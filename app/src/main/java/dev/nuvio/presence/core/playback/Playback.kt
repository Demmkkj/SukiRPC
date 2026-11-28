package dev.nuvio.presence

import kotlin.math.abs

private val validPlaybackStates=setOf("playing","paused","buffering","connecting","forwarding","rewinding")

internal data class Playback(val title:String,val episode:String,val mediaId:String,val artwork:String,val state:String,val position:Long,val duration:Long,val speed:Double,val receivedAt:Long,val year:String="",val genre:String="",val appId:String="",
    val artworkSource: String = "", val artworkRejected: Boolean = false,
    val localArtwork: ByteArray? = null, val remoteArtworkKey: String = "",
    val artworkPermitted: Boolean = true, val artworkSignals: String = ""
){
    fun withoutArtwork() = copy(artwork = "", artworkSource = "", artworkRejected = false,
        localArtwork = null, remoteArtworkKey = "", artworkPermitted = false, artworkSignals = "")

    val identity=listOf(mediaId,title,episode,appId)
    val valid get()=title.isNotBlank() && state in validPlaybackStates && !(state=="playing"&&duration>0&&position>=duration)
    val ticking get()=state=="playing"&&speed==1.0&&position>=0
    fun positionAt(now:Long):Long {
        if(position<0)return -1
        val p=if(state=="playing"&&speed>0)position+((now-receivedAt).coerceIn(0,5000)*speed).toLong() else position
        return if(duration>0)p.coerceIn(0,duration)else p.coerceAtLeast(0)
    }
}
internal data class Options(val artwork:Boolean=true,val timestamps:Boolean=true,val watching:Boolean=true,val clearPaused:Boolean=false,val episode:Boolean=true)
internal data class Presence(val identity:List<String>,val title:String,val detail:String,val art:String,val watching:Boolean,val start:Long,val end:Long,val anchorPosition:Long,val anchorMono:Long,val clockOffset:Long,val state:String,val activityName:String="Nuvio TV"){
    fun sameContent(other:Presence)=identity==other.identity&&title==other.title&&detail==other.detail&&art==other.art&&watching==other.watching&&activityName==other.activityName&&state==other.state&&(start>0)==(other.start>0)&&(end>0)==(other.end>0)
}
internal fun timestamp(ms:Long):String = if(ms<0) "--:--" else {
    val s=ms/1000
    if(s>=3600) "%d:%02d:%02d".format(s/3600,s/60%60,s%60) else "%02d:%02d".format(s/60,s%60)
}
internal fun presenceFor(p:Playback?,o:Options,mono:Long,wall:Long):Presence? {
    if(p==null||!p.valid||(o.clearPaused&&p.state=="paused"))return null
    val pos=p.positionAt(mono)
    if(p.state=="playing"&&p.duration>0&&pos>=p.duration)return null
    val tag=when(p.state){"paused"->"Pausado · ${timestamp(pos)}";"buffering","connecting"->"Carregando";"forwarding","rewinding"->"Buscando";else->if(p.speed!=1.0)"${p.speed}x · ${timestamp(pos)}" else ""}
    val desc=listOf(if(o.episode)p.episode else "",tag).filter{it.isNotBlank()}.joinToString(" · ").ifBlank{"Assistindo"}
    val start=if(o.timestamps&&p.ticking&&pos>=0)(wall-pos).coerceAtLeast(1)else 0
    val end=if(start>0&&p.duration>pos)start+p.duration else 0
    return Presence(p.identity,p.title,desc,if(o.artwork)p.artwork else "",o.watching,start,end,pos,mono,wall-mono,p.state)
}
internal fun needsUpdate(old:Presence?,next:Presence?):Boolean {
    if(old==null||next==null)return old!=next
    if(!old.sameContent(next))return true
    if(next.start>0||next.end>0){
        if(next.start==0L&&abs(old.end-next.end)>1800)return true
        val expected=old.anchorPosition+(next.anchorMono-old.anchorMono)
        if(abs(expected-next.anchorPosition)>1800||abs(old.clockOffset-next.clockOffset)>1800)return true
        if(old.end>0&&next.end>0&&abs((old.end-old.start)-(next.end-next.start))>1000)return true
    }
    return false
}

