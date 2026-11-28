package dev.nuvio.presence

fun main() {
    val p = Playback("Glass", "S1:E4 — Secret", "id", "poster", "playing", 1000, 40000, 1.0, 10, "2026", "Drama")
    val custom = MediaFormat(MediaPreset.Custom, "{title} · ({year})", "S{season} · E{episode} · {genre} · {device}")
    fun render(sample: Playback, format: MediaFormat, hidden: Set<Hidden> = emptySet(), device: String = "Nuvio TV") =
        mediaPresence(sample, Options(), format, MediaPrivacy(hidden), device, 10, 10000)!!
    for (device in listOf("Nuvio Mobile", "Nuvio TV")) {
        val result = render(p, custom, device = device)
        check(result.title == "Glass · (2026)" && result.detail == "S1 · E4 · Drama · $device")
        val missing = render(p.copy(episode="", year="", genre=""), custom, device=device)
        check(missing.title == "Glass" && missing.detail == device)
        for (preset in MediaPreset.entries) {
            val private = render(p, custom.copy(preset=preset), setOf(Hidden.Title, Hidden.Progress),device)
            check(private.art.isEmpty() && private.start == 0L && private.end == 0L)
            check(listOf("Glass","Secret","Drama","2026","S1","E4").none { it in private.title || it in private.detail })
        }
    }
    check(render(p, MediaFormat()).detail == p.episode)
    check(render(p, MediaFormat(MediaPreset.Minimal)).detail.isEmpty())
    check(render(p, MediaFormat(MediaPreset.Cinema)).detail == "Drama · Nuvio TV")
    check(render(p, MediaFormat(MediaPreset.Anime)).detail.startsWith("Temporada 1 · Episódio 4"))
    check(render(p, MediaFormat(MediaPreset.Detailed)).detail.startsWith("S1 · E4"))
    check(render(p, custom.copy(clock=ClockDisplay.Remaining)).let { it.start == 0L && it.end > 0 })
    val paused = render(p.copy(state="paused"),MediaFormat(),setOf(Hidden.Progress, Hidden.Spoilers))
    check(paused.detail == "Pausado" && paused.art.isEmpty())
    val titledEpisode = p.copy(title = "Glass · s1:e4 — SECRET")
    check(render(titledEpisode, MediaFormat(), setOf(Hidden.Episode)).title == "Glass")
    for (privacy in listOf(MediaPrivacy(excluded=setOf(" glass ".trim())), MediaPrivacy(setOf(Hidden.Disabled))))
        check(mediaPresence(p,Options(),custom,privacy,"Nuvio TV",10,10000) == null)
    check(discordText("🎥".repeat(80)) == "🎥".repeat(32))
    check(discordText("a".repeat(127)+"🎥") == "a".repeat(127))
    val publicFallback = "https://image.tmdb.org/t/p/w500/fallback.jpg"
    val logoFallback = MediaFormat(fallback = PRISM_LOGO)
    check(render(p.withoutArtwork(), logoFallback).art.isEmpty())
    check(render(p.withoutArtwork(), MediaFormat(fallback = publicFallback)).art.isEmpty())
    check(mediaPresence(p, Options(artwork = false), logoFallback, MediaPrivacy(), "Nuvio TV", 10, 10000)?.art == "")
    for (hidden in listOf(Hidden.Artwork, Hidden.Title, Hidden.Spoilers)) {
        check(!artworkAllowed(Options(), MediaPrivacy(setOf(hidden)), p.title))
        check(render(p, logoFallback, setOf(hidden)).art.isEmpty())
    }
    check(!artworkAllowed(Options(), MediaPrivacy(excluded = setOf("glass")), p.title))
    val legacy = mapOf("episode" to false, "art" to false)
    check(readFormat(legacy) == MediaFormat())
    check(readFormat(legacy + custom.settings()) == custom)
    check(readFormat(mapOf("prism.clock" to "unknown")) == MediaFormat())
    check(safeFallback("http://localhost/secret") == "" && safeFallback(PRISM_LOGO) == PRISM_LOGO)
    println("PASS: media presets, optional clauses, both sources, timers, privacy, exclusions, UTF-8 and format persistence")
}
