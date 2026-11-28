package dev.nuvio.presence

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal class HorizonArtworkEngine(
    private val scope: CoroutineScope,
    now: () -> Long,
    private val lookup: suspend (String) -> String,
    private val successTtlMs: Long = 60 * 60 * 1000L,
    private val failureTtlMs: Long = 5 * 60 * 1000L
) {
    private data class Key(val origin: String, val identity: List<String>, val remoteKey: String)
    private data class Result(val url: String = "", val bitmap: ByteArray? = null, val source: String = "miss")

    private val cache = ArtworkCache<Key, Result>(32, now)
    private var selected: Key? = null
    private var work: Job? = null
    private var fresh: Result? = null

    fun resolve(
        playback: Playback?, origin: String, enabled: Boolean, searchCatalog: Boolean,
        readRemoteBitmap: (suspend () -> ByteArray?)?
    ): Playback? {
        if (playback == null || !enabled || !playback.artworkPermitted) {
            cancel()
            return playback?.withoutArtwork()
        }
        if (playback.artwork.isNotEmpty() || (!searchCatalog && readRemoteBitmap == null)) {
            cancel()
            return playback
        }
        val key = Key(origin, playback.identity, playback.remoteArtworkKey)
        if (selected != key) {
            cancel()
            selected = key
        }
        val completed = fresh.also { fresh = null }
        val result = completed ?: cache[key]
        if (result != null) {
            val source = if (completed == null && result.source != "miss") "cache" else result.source
            return playback.copy(artwork = result.url, localArtwork = result.bitmap ?: playback.localArtwork,
                artworkSource = source)
        }
        if (work?.isActive != true) {
            work = scope.launch {
                val api = if (searchCatalog) lookup(playback.title) else ""
                val result = if (api.isNotEmpty()) Result(url = api, source = "api")
                else {
                    val bitmap = try {
                        readRemoteBitmap?.invoke()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    if (bitmap == null) Result() else Result(bitmap = bitmap, source = "bitmap")
                }
                ensureActive()
                if (selected == key) {
                    cache.put(key, result, if (result.source == "miss") failureTtlMs else successTtlMs)
                    fresh = result
                }
            }
        }
        return playback.copy(artworkSource = "pending")
    }

    fun cancel(clearCache: Boolean = false) {
        selected = null
        fresh = null
        work?.cancel()
        work = null
        if (clearCache) cache.clear()
    }
}
