package dev.nuvio.presence

import dev.nuvio.artwork.ArtworkPolicy
import java.net.InetAddress

fun main() {
    val spotifyId = "ab67616d00001e02ff9ca10b55ce82ae553c8228"
    val spotify = "https://i.scdn.co/image/$spotifyId"
    val video = "https://i.ytimg.com/vi/7lCDEYXw3mM/hqdefault.jpg"
    val imdb = "https://images.metahub.space/poster/medium/tt0111161/img"
    val ytm = "https://lh3.googleusercontent.com/ABCDEFGHIJKLMNOPQRSTUV_123456=w240-h240-l90-rj"
    listOf("https://image.tmdb.org/t/p/w500/a.jpeg", imdb, spotify, video).forEach {
        check(publicArtwork(it) == it)
    }
    check(publicArtwork(ytm).isEmpty())
    check(publicArtwork(ytm, "com.google.android.apps.youtube.music") == ytm)
    check(cachedPublicArtwork(ytm) == ytm)
    val invalid = listOf(
        "", "http://image.tmdb.org/t/p/w500/a.jpg", "https://127.0.0.1/a.jpg",
        "https://192.168.1.2/Items/abc/Images/Primary", "https://[::1]/a.jpg",
        "https://localhost/cover.jpg", "file:///storage/emulated/0/a.jpg", "content://media/a",
        "data:image/jpeg;base64,AAA", "https://foo.plex.direct/library/metadata/4/thumb/23",
        "https://i.scdn.co:443/image/$spotifyId", "https://i.scdn.co./image/$spotifyId",
        "https://name:pass@i.scdn.co/image/$spotifyId", "https://i.scdn.co.evil.test/image/$spotifyId",
        "https://i.scdn.co@evil.test/image/$spotifyId", "https://i.scdn.co/image/$spotifyId?token=private",
        "$spotify?X-Amz-Signature=private", "$spotify#opaque", "$spotify%2Fextra", "$video?redirect=1",
        "https://i.ytimg.com/redirect?to=https://localhost/x", "https://i.ytimg.com/vi/invalid/hqdefault.jpg",
        "https://image.tmdb.org/t/p/w500/../secret.jpg", "https://i.scdn.co/image/%2E%2E"
    )
    invalid.forEach { check(cachedPublicArtwork(it).isEmpty()) { "Accepted invalid artwork: $it" } }

    listOf("com.nuvio.app", "com.nuvio.tv", "com.stremio.one")
        .forEach { check(ArtworkPolicy.fromMediaId("tt0111161:2:10", it) == imdb) }
    listOf("com.google.android.youtube", "com.google.android.youtube.tv", "com.google.android.apps.youtube.music")
        .forEach { pkg ->
            check(ArtworkPolicy.fromMediaId("7lCDEYXw3mM", pkg) == video)
            check(ArtworkPolicy.fromMediaId("https://www.youtube.com/watch?v=7lCDEYXw3mM", pkg) == video)
            check(ArtworkPolicy.fromMediaId("https://youtu.be/7lCDEYXw3mM?token=abc", pkg).isEmpty())
        }
    listOf("org.jellyfin.mobile", "org.jellyfin.androidtv", "com.plexapp.android", "org.xbmc.kodi")
        .forEach { pkg ->
            check(ArtworkPolicy.fromMediaId("imdb:tt0111161", pkg) == imdb)
            check(ArtworkPolicy.fromMediaId("13771223", pkg).isEmpty())
        }
    check(ArtworkPolicy.fromArtworkMetadata("spotify:image:$spotifyId", "com.spotify.music") == spotify)
    check(ArtworkPolicy.fromArtworkMetadata("spotify:image:$spotifyId", "org.videolan.vlc").isEmpty())
    listOf("org.videolan.vlc", "is.xyz.mpv", "dev.other.player").forEach {
        check(ArtworkPolicy.publicUrl(imdb, it) == imdb)
        check(ArtworkPolicy.fromMediaId("7lCDEYXw3mM", it).isEmpty())
    }
    val y = "https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg"
    listOf("https://youtu.be/jNQXAC9IVRw?si=abc&t=42", "https://m.youtube.com/shorts/jNQXAC9IVRw",
        "https://youtube.com/live/jNQXAC9IVRw?feature=share", "https://music.youtube.com/watch?v=jNQXAC9IVRw&list=abc")
        .forEach { check(ArtworkPolicy.thumbnailFromLink(it) == y) }
    listOf("https://youtube.com.evil.example/watch?v=jNQXAC9IVRw", "https://name@youtu.be/jNQXAC9IVRw",
        "https://www.youtube.com/watch?v=jNQXAC9IVRw&v=7lCDEYXw3mM", "http://youtu.be/jNQXAC9IVRw",
        "https://youtu.be/jNQXAC9IVRw?token=private")
        .forEach { check(ArtworkPolicy.thumbnailFromLink(it).isEmpty()) }
    check(ArtworkPolicy.fromMediaId("imdb:tt0111161:2:10", "com.nuvio.app") == imdb)
    check(ArtworkPolicy.validLocalKey("a".repeat(32)) && !ArtworkPolicy.validLocalKey("a".repeat(33)))
    listOf("127.0.0.1", "10.5.4.3", "169.254.169.254", "100.64.0.5", "192.168.0.1",
        "0.0.0.0", "192.0.2.1", "::1", "fc00::1", "fe80::1", "2001:db8::1", "::ffff:10.0.0.1")
        .forEach { check(!ArtworkPolicy.publicAddress(InetAddress.getByName(it))) }
    listOf("8.8.8.8", "2606:4700:4700::1111")
        .forEach { check(ArtworkPolicy.publicAddress(InetAddress.getByName(it))) }
    println("PASS: official artwork routes, provider identifiers, legacy sources and rejected URLs")
}
