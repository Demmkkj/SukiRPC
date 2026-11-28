package dev.nuvio.presence
fun main(){
    val vlc="org.videolan.vlc";val native="com.nuvio.tv"
    check(Horizon.describe(SourceId(Origin.TV,native))!!.native)
    check(!Horizon.describe(SourceId(Origin.Mobile,vlc))!!.native)
    check(Horizon.describe(SourceId(Origin.Studio,"custom"))!!.capabilities.contains("game"))
    check(Horizon.describe(SourceId(Origin.TV,"../bad"))==null)
    check(!Horizon.allowed(vlc,emptySet(),emptySet()))
    check(Horizon.allowed(native,emptySet(),emptySet()))
    check(!Horizon.allowed(native,setOf(native),setOf(native)))
    fun select(list:List<String>,previous:String?)=Horizon.choose(list,{it},{it!="paused"},{if(it=="paused")3 else 1},previous)
    check(select(listOf("paused","game","video"),"video")=="video")
    check(select(emptyList(),"video")==null)
    check(select(listOf("game"),"video")=="game")
    println("PASS: provider descriptions, opt-in, exclusion, ranking, loss and legacy defaults")
}
