package dev.nuvio.presence

import java.net.URI

private val sukiTmdbArtworkPattern = Regex("/t/p/(?:original|w[0-9]{2,4}|h[0-9]{2,4})/[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp)",RegexOption.IGNORE_CASE)
private val sukiMetahubArtworkPattern = Regex("/poster/(?:small|medium|large)/tt[0-9]{7,12}/img")

internal fun publicArtwork(raw:String):String = try {
    val u=URI(raw)
    // Avoid forwarding private/signed URLs, credentials or arbitrary LAN addresses to Discord.
    val path=u.rawPath.orEmpty()
    val supported=when(u.host?.lowercase(java.util.Locale.ROOT)){
        "image.tmdb.org"->sukiTmdbArtworkPattern.matches(path)
        "images.metahub.space"->sukiMetahubArtworkPattern.matches(path)
        else->false
    }
    if(u.scheme=="https"&&supported&&u.userInfo==null&&u.rawQuery==null&&u.rawFragment==null&&u.port==-1)raw else ""
} catch(_:Exception){""}
