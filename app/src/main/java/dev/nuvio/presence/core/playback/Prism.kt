package dev.nuvio.presence

internal enum class MediaPreset(val title: String, val detail: String) {
    Default("{title}", "{subtitle} · {playback}"),
    Minimal("{title}", ""),
    Detailed("{title} · {year}", "S{season} · E{episode} · {genre} · {status}"),
    Cinema("{title} · {year}", "{genre} · {device}"),
    Anime("{title}", "Temporada {season} · Episódio {episode} · {status}"),
    Custom("{title}", "{subtitle} · {status}")
}

internal enum class ClockDisplay { Auto, Elapsed, Remaining, Off }
internal enum class Hidden { Title, Artwork, Episode, Progress, Spoilers, Disabled }

internal data class MediaFormat(
    val preset: MediaPreset = MediaPreset.Default,
    val title: String = MediaPreset.Custom.title,
    val detail: String = MediaPreset.Custom.detail,
    val clock: ClockDisplay = ClockDisplay.Auto,
    val fallback: String = ""
)
internal data class MediaPrivacy(val hidden: Set<Hidden> = emptySet(), val excluded: Set<String> = emptySet()) {
    fun blocks(title: String) = Hidden.Disabled in hidden || excluded.any { it.equals(title.trim(), true) }
}

internal fun artworkAllowed(options: Options, privacy: MediaPrivacy, title: String): Boolean =
    options.artwork && !privacy.blocks(title) &&
        Hidden.Title !in privacy.hidden && Hidden.Artwork !in privacy.hidden && Hidden.Spoilers !in privacy.hidden

internal fun episodeAllowed(options: Options, privacy: MediaPrivacy): Boolean =
    options.episode && Hidden.Title !in privacy.hidden && Hidden.Episode !in privacy.hidden &&
        Hidden.Spoilers !in privacy.hidden

internal fun visibleMediaTitle(playback: Playback, privacy: MediaPrivacy, episodeVisible: Boolean): String = when {
    Hidden.Title in privacy.hidden -> ""
    episodeVisible -> playback.title
    else -> {
        val title = if (playback.episode.isNotBlank()) {
            val offset = playback.title.indexOf(playback.episode, ignoreCase = true)
            if (offset >= 0) playback.title.substring(0, offset) else playback.title
        } else playback.title
        episodeCode.split(title, limit = 2).first().trimEnd(' ', '-', '·', ':')
    }
}

private val token = Regex("""\{(\w+)\}""")
private val episodeCode = Regex("""(?i)\bS(\d{1,3})\s*[:. -]?\s*E(\d{1,4})\b""")
private val separators = Regex("""\s*[·|•]\s*""")
internal fun discordText(value: String): String {
    val text = value.trim()
    var index = 0
    var bytes = 0
    while (index < text.length) {
        val cp = text.codePointAt(index)
        val size = when { cp < 0x80 -> 1; cp < 0x800 -> 2; cp < 0x10000 -> 3; else -> 4 }
        if (bytes + size > 128) break
        bytes += size
        index += Character.charCount(cp)
    }
    return if (bytes < 2) "" else text.substring(0, index)
}

// Each separator-delimited clause is optional, including its labels and punctuation.
internal fun expandMedia(format: String, values: Map<String,String>): String =
    discordText(format.split(separators).mapNotNull { clause ->
        if (token.findAll(clause).any { values[it.groupValues[1]].isNullOrBlank() }) null
        else token.replace(clause) { values.getValue(it.groupValues[1]) }.trim().ifBlank { null }
    }.joinToString(" · "))

internal fun mediaPresence(p: Playback?, options: Options, format: MediaFormat, privacy: MediaPrivacy,
                          device: String, mono: Long, wall: Long): Presence? {
    if (p == null || privacy.blocks(p.title)) return null
    val hidden = privacy.hidden
    val titleVisible = Hidden.Title !in hidden
    val episodeVisible = episodeAllowed(options, privacy)
    val progress = options.timestamps && Hidden.Progress !in hidden && format.clock != ClockDisplay.Off
    val art = p.artworkPermitted && artworkAllowed(options, privacy, p.title)
    val base = presenceFor(p, options.copy(timestamps = progress), mono, wall) ?: return null
    val status = when (p.state) {
        "playing" -> "Assistindo"
        "paused" -> "Pausado"
        "forwarding", "rewinding" -> "Buscando"
        else -> "Carregando"
    }
    val tag = when {
        p.state == "playing" && p.speed == 1.0 -> ""
        p.state == "playing" -> "${p.speed}x"
        else -> status
    }
    val playback = if (progress && base.anchorPosition >= 0 && (p.state == "paused" || (p.state == "playing" && p.speed != 1.0)))
        "$tag · ${timestamp(base.anchorPosition)}" else tag
    val ep = if (episodeVisible) episodeCode.find(p.episode) ?: episodeCode.find(p.title) else null
    val safeTitle = visibleMediaTitle(p, privacy, episodeVisible)
    val values = mapOf("title" to safeTitle, "subtitle" to if (episodeVisible) p.episode else "",
        "season" to ep?.groupValues?.get(1).orEmpty(), "episode" to ep?.groupValues?.get(2).orEmpty(),
        "year" to if (titleVisible) p.year else "", "genre" to if (titleVisible) p.genre else "",
        "device" to device, "status" to status, "playback" to playback)
    val custom = format.preset == MediaPreset.Custom
    val title = expandMedia(if (custom) format.title else format.preset.title, values)
    val detail = expandMedia(if (custom) format.detail else format.preset.detail, values)
    return base.copy(title = title, detail = detail.ifBlank { if (format.preset == MediaPreset.Default) status else "" },
        art = if (art) p.artwork.ifBlank { format.fallback } else "", activityName = device,
        start = if (format.clock == ClockDisplay.Remaining) 0 else base.start,
        end = if (format.clock == ClockDisplay.Elapsed) 0 else base.end)
}
