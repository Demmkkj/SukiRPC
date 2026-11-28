package dev.nuvio.presence
fun main(){
    val a="a".repeat(64);val b="b".repeat(64)
    check(validHint(a,8766,true) && !validHint(a,22,true) && !validHint(a,8766,false))
    check(acceptsPin(a,a) && acceptsPin(a.take(16),a) && !acceptsPin(a,b) && !acceptsPin("",a))
    check(routes(a,listOf(b,a,b),true)==listOf(a,b));check(routes(a,listOf(b),false)==listOf(a))
    val legacy="encrypted record";val moved=migratedPair(a,legacy)
    check(moved["pair."+a]==legacy && moved["active"]==a)
    check(mayRelocate(a,a,"old","old") && !mayRelocate(a,b,"old","old") && !mayRelocate(a,a,"old","new"))
    val network=NetworkEpoch();val old=network.current();network.changed();check(!network.accepts(old))
    check(network.accepts(network.current()));network.changed();check(!network.accepts(old))
    val source=SourceChoice()
    check(source.pick("Automatic","playing","paused",0)=="mobile")
    check(source.pick("TV Priority","playing","playing",100)=="mobile")
    check(source.pick("TV Priority","playing","playing",1400)=="tv")
    check(source.pick("Mobile Priority","playing","playing",1500)=="tv")
    check(source.pick("Mobile Priority","playing","playing",2900)=="mobile")
    check(source.pick("Automatic",null,"playing",2950)=="tv")
    check(source.pick("Automatic",null,null,3000)=="")
    check(protocolOK(1,0)&&protocolOK(2,6000)&&!protocolOK(2,6001)&&!protocolOK(3,0))
    check((1..5).map{retryDelay(it,10)}==listOf(2010L,4010L,8010L,16010L,32010L));check(retryDelay(999,4000)==32999L)
    val secret="192.168.1.10 secret@example.com /private/file hidden title 12345678"
    val allowed="12:30:00 Info · Discord · Discord confirmou a presença"
    check(safeEvent(secret)==null && report(listOf(secret,allowed,allowed+secret))==allowed)
    println("PASS: discovery hints, pin rejection, multi-device routing, generation invalidation, source hysteresis, backoff, protocol age and report allowlist")
}
