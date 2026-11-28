package dev.nuvio.presence

import dev.nuvio.artwork.ArtworkPolicy

internal fun publicArtwork(raw: String, appId: String = ""): String =
    ArtworkPolicy.publicUrl(raw, appId)

internal fun cachedPublicArtwork(raw: String): String =
    ArtworkPolicy.publicUrl(raw, "history")
