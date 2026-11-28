package dev.nuvio.presence

import java.net.URI
import dev.nuvio.artwork.ArtworkPolicy

internal data class RpcPreset(
    val name: String = "", val details: String = "", val state: String = "",
    val type: String = "playing", val largeImage: String = "", val largeText: String = "",
    val smallImage: String = "", val smallText: String = "", val timer: String = "elapsed",
    val minutes: Int = 60, val button1: String = "", val url1: String = "",
    val button2: String = "", val url2: String = "", val appPackage: String = ""
) {
    fun error(): String? {
        if (name.trim().length !in 2..128) return "Nome: use de 2 a 128 caracteres."
        if (type !in listOf("playing", "watching", "listening", "competing")) return "Tipo inválido."
        if (timer !in listOf("none", "elapsed", "countdown") || minutes !in 1..1440) return "Cronômetro inválido."
        if (listOf(details,state,largeText,smallText).any { it.isNotEmpty() && it.length !in 2..128 }) return "Textos opcionais: 2 a 128 caracteres."
        if (listOf(largeImage,smallImage).any { it.isNotBlank() && !validAsset(it) }) return "Imagem: use URL HTTPS pública (inclusive GIF) ou chave de asset (até 300 caracteres)."
        for ((label,url) in listOf(button1 to url1,button2 to url2)) {
            if (label.isNotEmpty() || url.isNotEmpty()) {
                if (label.length !in 1..32 || !safeHttps(url, 256)) return "Botões: rótulo de até 32 caracteres e URL HTTPS válida."
            }
        }
        if (appPackage.isNotEmpty() && !Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(appPackage)) return "Nome de pacote inválido."
        return null
    }
}
internal fun safeHttps(value:String, max:Int):Boolean = runCatching {
    val u=URI(value); val host=u.host.orEmpty().lowercase()
    value.length in 1..max && u.scheme=="https" && u.userInfo==null && u.port==-1 && u.fragment==null &&
        host.contains('.') && !host.endsWith(".local") && !host.endsWith(".localhost") && !host.matches(Regex("[0-9.]+")) &&
        !host.endsWith(".internal") && !host.endsWith(".lan") && !host.endsWith(".test") && !host.endsWith(".invalid")
}.getOrDefault(false)
internal fun validAsset(value: String) = studioAsset(value).isNotEmpty()

private val assetKey = Regex("[A-Za-z0-9_-]{1,128}")

/** Keep custom asset keys and external HTTPS artwork; resolve known video pages to actual images. */
internal fun studioAsset(value: String): String {
    if (assetKey.matches(value)) return value
    if (!safeHttps(value, 300)) return ""
    val public = cachedPublicArtwork(value)
    if (public.isNotEmpty()) return public
    val host = URI(value).host.lowercase(java.util.Locale.ROOT)
    return if (ArtworkPolicy.managedArtworkHost(host)) ArtworkPolicy.thumbnailFromLink(value) else value
}

private val staticStudioHosts = setOf("cdn.cloudflare.steamstatic.com", "shared.fastly.steamstatic.com",
    "shared.cloudflare.steamstatic.com", "shared.steamstatic.com", "www.minecraft.net",
    "cdn.discordapp.com", "media.discordapp.net")
private val animatedStudioHosts = setOf("media.tenor.com", "media1.tenor.com", "media.giphy.com",
    "media0.giphy.com", "media1.giphy.com", "media2.giphy.com", "media3.giphy.com", "media4.giphy.com", "i.imgur.com")

internal fun studioPreviewUrl(value: String, editor: Boolean): String {
    val normalized = studioAsset(value)
    if (!safeHttps(normalized, 300)) return ""
    if (cachedPublicArtwork(normalized).isNotEmpty()) return normalized
    val host = URI(normalized).host.lowercase(java.util.Locale.ROOT)
    return normalized.takeIf { host in staticStudioHosts || (editor && host in animatedStudioHosts) }.orEmpty()
}

internal val rpcTypes = linkedMapOf("playing" to "Jogando", "watching" to "Assistindo", "listening" to "Ouvindo", "competing" to "Competindo")

// Community catalog intentionally excludes game activities; personal RPCs remain unrestricted.
internal fun communityPresets(items:List<RpcPreset>):List<RpcPreset> =
    items.filter { it.type == "watching" || it.type == "listening" }
