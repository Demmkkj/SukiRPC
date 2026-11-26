package dev.nuvio.presence

fun main(){
    val wall=1795669662023L
    val p=Playback("Fudêncio e Seus Amigos","S1:E4","episode4","https://image.tmdb.org/t/p/w500/test.jpg","playing",302951,677120,1.0,10000)
    val opts=Options()
    val a=presenceFor(p,opts,10000,wall)!!
    check(a.start==wall-302951);check(a.end==a.start+677120);check(timestamp(p.position)=="05:02")
    val normal=presenceFor(p.copy(position=304951,receivedAt=12000),opts,12000,wall+2000)!!
    check(!needsUpdate(a,normal))
    val seek=presenceFor(p.copy(position=456000,receivedAt=12000),opts,12000,wall+2000)!!
    check(needsUpdate(a,seek));check(seek.start==wall+2000-456000)
    val next=presenceFor(p.copy(episode="S1:E5",mediaId="episode5",position=200,receivedAt=12000),opts,12000,wall+2000)!!
    check(needsUpdate(a,next));check(next.start==wall+2000-200)
    val pause=presenceFor(p.copy(state="paused",position=304951,speed=0.0,receivedAt=12000),opts,15000,wall+5000)!!
    check(pause.start==0L&&pause.end==0L);check(pause.detail.contains("05:04"));check(needsUpdate(a,pause))
    val resume=presenceFor(p.copy(position=304951,receivedAt=18000),opts,18000,wall+8000)!!
    check(needsUpdate(pause,resume));check(resume.start==wall+8000-304951)
    val buffering=presenceFor(p.copy(state="buffering",speed=0.0),opts,10000,wall)!!;check(buffering.start==0L&&buffering.end==0L)
    check(presenceFor(p.copy(state="skipping"),opts,10000,wall)==null)
    check(presenceFor(p.copy(position=p.duration),opts,10000,wall)==null)
    check(presenceFor(null,opts,10000,wall)==null)
    check(presenceFor(p.copy(state="paused"),opts.copy(clearPaused=true),10000,wall)==null)
    check(presenceFor(p.copy(speed=2.0),opts,10000,wall)!!.start==0L)
    check(needsUpdate(a,presenceFor(p,opts,10000,wall+10000)))
    check(p.positionAt(100000)==307951L)
    println("PASS: timestamps, progression, seek, episode changes, pause, resume, buffering, skip, end, disconnect, speed, clock adjustment and stale projection")
}
