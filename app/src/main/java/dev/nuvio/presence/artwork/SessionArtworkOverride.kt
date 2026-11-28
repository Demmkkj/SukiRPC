package dev.nuvio.presence

import dev.nuvio.artwork.ArtworkPolicy

/** Explicit user choice for the currently observed Mobile item. Nothing is persisted. */
internal class SessionArtworkOverride {
    private data class Choice(val identity: List<String>, val url: String)
    private var choice: Choice? = null

    fun set(playback: Playback, input: String): Boolean {
        if (!playback.artworkPermitted || !manualArtworkSupported(playback)) return false
        val url = manualArtworkUrl(playback, input)
        if (url.isEmpty()) return false
        choice = Choice(playback.identity, url)
        return true
    }

    fun apply(playback: Playback?, mobile: Boolean, enabled: Boolean): Playback? {
        val selected = choice ?: return playback
        if (!mobile || !enabled || playback == null || !playback.artworkPermitted ||
            playback.identity != selected.identity) {
            clear()
            return playback
        }
        return playback.copy(artwork = selected.url, artworkSource = "manual", localArtwork = null)
    }

    fun clear() { choice = null }
}

private val manualNuvioPackages = setOf("com.nuvio.app", "com.nuviodebug.com")

internal fun manualArtworkSupported(playback: Playback): Boolean =
    playback.mediaId.startsWith("mobile:") &&
        (ArtworkPolicy.isYoutubeSource(playback.appId) || playback.appId in manualNuvioPackages)

internal fun manualArtworkUrl(playback: Playback, input: String): String {
    val clean = input.trim()
    return if (ArtworkPolicy.isYoutubeSource(playback.appId)) {
        ArtworkPolicy.fromMediaId(clean, playback.appId).ifEmpty { ArtworkPolicy.thumbnailFromLink(clean) }
    } else {
        publicArtwork(clean, playback.appId).ifEmpty { ArtworkPolicy.fromMediaId(clean, playback.appId) }
    }
}
