package dev.nuvio.presence

internal const val PRISM_LOGO = "https://raw.githubusercontent.com/Demmkkj/SukiRPC/main/branding/sukirpc_logo.png"
internal fun safeFallback(url: String) = if (url == PRISM_LOGO) url else publicArtwork(url)

internal fun readFormat(values: Map<String, *>): MediaFormat {
    val initial = MediaFormat()
    return MediaFormat(
        MediaPreset.entries.find { it.name == values["prism.preset"] } ?: initial.preset,
        values["prism.title"] as? String ?: initial.title,
        values["prism.detail"] as? String ?: initial.detail,
        ClockDisplay.entries.find { it.name == values["prism.clock"] } ?: initial.clock,
        safeFallback(values["prism.fallback"] as? String ?: "")
    )
}

internal fun MediaFormat.settings() = mapOf(
    "prism.preset" to preset.name, "prism.title" to title, "prism.detail" to detail,
    "prism.clock" to clock.name, "prism.fallback" to safeFallback(fallback)
)
