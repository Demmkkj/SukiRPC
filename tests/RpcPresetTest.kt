package dev.nuvio.presence
fun main(){
    val activities = listOf("playing","watching","listening","competing").map { RpcPreset("Teste",type=it) }
    check(activities.all { it.error()==null })
    check(communityPresets(activities).map { it.type } == listOf("watching","listening"))
    check(communityPresets(emptyList()).isEmpty())
    check(RpcPreset().error()!=null)
    check(RpcPreset("Teste",type="invalid").error()!=null)
    check(RpcPreset("Teste",timer="countdown",minutes=0).error()!=null)
    check(RpcPreset("Teste",button1="Site",url1="http://example.com").error()!=null)
    check(RpcPreset("Teste",button1="Site",url1="https://example.com").error()==null)
    check(RpcPreset("Teste",appPackage="invalid").error()!=null)
    check(!validAsset("https://127.0.0.1/image.png"))
    check(!validAsset("https://example.com@localhost/image.png"))
    check(validAsset("game_icon"))
    val gif = "https://media.tenor.com/example/animation.gif"
    val smallGif = "https://media1.giphy.com/media/example/giphy.gif"
    check(RpcPreset("GIF test",largeImage=gif,smallImage=smallGif).error()==null)
    check(studioPreviewUrl(gif,true)==gif && studioPreviewUrl(smallGif,true)==smallGif)
    check(studioPreviewUrl(gif,false).isEmpty())
    check(validAsset("https://example.com/animation.gif"))
    check(studioPreviewUrl("https://example.com/animation.gif",true).isEmpty())
    check(!validAsset("http://media.tenor.com/animation.gif"))
    check(!validAsset("https://127.0.0.1/animation.gif"))
    check(studioPreviewUrl("https://media.tenor.com.example.com/animation.gif",true).isEmpty())
    check(studioPreviewUrl("https://image.tmdb.org/t/p/w500/example.jpg",false).isNotEmpty())
    val video = "https://www.youtube.com/watch?pp=tracking&v=jNQXAC9IVRw"
    val still = "https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg"
    check(studioAsset(video) == still)
    check(studioPreviewUrl(video, false) == still && studioPreviewUrl(still, true) == still)
    check(studioAsset("https://img.youtube.com/vi/jNQXAC9IVRw/hqdefault.jpg") == still)
    check(!validAsset("https://youtube.com/playlist?list=PL123"))
    check(!validAsset("https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg?token=secret"))
    check(!validAsset("https://image.tmdb.org/t/p/w500/example.jpg?token=secret"))
    check(studioAsset("game_icon") == "game_icon" && studioAsset(gif) == gif)
    println("PASS: RPC Studio presets and validation")
}
