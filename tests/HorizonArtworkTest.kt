package dev.nuvio.presence

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

fun main() = runBlocking {
    val first = Playback("Primeira", "", "1", "", "playing", 0, 5000, 1.0, 0, appId = "com.nuvio.app")
    val second = first.copy(title = "Segunda", mediaId = "2")
    val poster = "https://images.metahub.space/poster/medium/tt0111161/img"
    var now = 0L
    var requests = 0
    val firstLookup = CompletableDeferred<String>()
    val engine = HorizonArtworkEngine(this, { now }, { title ->
        requests++
        if (title == first.title) firstLookup.await() else poster
    }, successTtlMs = 100L, failureTtlMs = 5L)

    check(engine.resolve(first, "mobile", false, true, null)?.artworkPermitted == false)
    yield()
    check(requests == 0)
    check(engine.resolve(first, "mobile", true, true, null)?.artworkSource == "pending")
    yield()
    engine.resolve(first, "mobile", true, true, null)
    check(requests == 1)
    check(engine.resolve(second, "mobile", true, true, null)?.artwork.isNullOrEmpty())
    yield()
    firstLookup.complete("https://image.tmdb.org/t/p/w500/old.jpg")
    yield()
    check(engine.resolve(second, "mobile", true, true, null)?.let {
        it.artwork == poster && it.artworkSource == "api"
    } == true)
    check(engine.resolve(second, "mobile", true, true, null)?.artworkSource == "cache")
    check(requests == 2)
    now = 101
    check(engine.resolve(second, "mobile", true, true, null)?.artworkSource == "pending")
    yield()
    check(requests == 3)
    engine.cancel(clearCache = true)

    val tv = second.copy(remoteArtworkKey = "a".repeat(32))
    var bitmapReads = 0
    val fetch: suspend () -> ByteArray? = { bitmapReads++; null }
    engine.resolve(tv, "tv", true, false, fetch)
    yield()
    check(engine.resolve(tv, "tv", true, false, fetch)?.artworkSource == "miss")
    check(engine.resolve(tv, "tv", true, false, fetch)?.artworkSource == "miss")
    check(bitmapReads == 1)
    now += 6
    val bytes = byteArrayOf(1, 2, 3)
    engine.resolve(tv, "tv", true, false) { bitmapReads++; bytes }
    yield()
    val frame = requireNotNull(engine.resolve(tv, "tv", true, false, fetch))
    check(frame.localArtwork === bytes && frame.artwork.isEmpty() && frame.artworkSource == "bitmap")
    check(engine.resolve(tv, "tv", false, true, fetch)?.localArtwork == null)
    check(bitmapReads == 2)
    engine.cancel()
    val manual = SessionArtworkOverride()
    val youtube = first.copy(appId = "com.google.android.youtube", mediaId = "mobile:", localArtwork = bytes)
    check(manual.set(youtube, "https://youtu.be/jNQXAC9IVRw?si=tracking"))
    check(manual.apply(youtube, mobile = true, enabled = true)?.let {
        it.artwork == "https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg" && it.localArtwork == null
    } == true)
    val switched = youtube.copy(title = "Outra mídia")
    check(manual.apply(switched, mobile = true, enabled = true) == switched)
    check(manual.apply(youtube, mobile = true, enabled = true) == youtube)
    check(manual.set(youtube, "jNQXAC9IVRw"))
    check(manual.apply(youtube, mobile = true, enabled = false) == youtube)
    check(manual.apply(youtube, mobile = true, enabled = true) == youtube)
    check(!manual.set(youtube.withoutArtwork(), "jNQXAC9IVRw"))
    check(!manual.set(youtube.copy(mediaId = "tv"), "jNQXAC9IVRw"))
    check(!manual.set(youtube, "https://127.0.0.1/jNQXAC9IVRw"))
    val nuvio = first.copy(mediaId = "mobile:opaque", localArtwork = bytes)
    check(manual.set(nuvio, "imdb:tt0111161"))
    check(manual.apply(nuvio, mobile = true, enabled = true)?.artwork == poster)
    check(manual.apply(nuvio, mobile = false, enabled = true) == nuvio)
    println("PASS: artwork cache, cancellation, media swaps, negative TTL and local-only Collector artwork")
}
