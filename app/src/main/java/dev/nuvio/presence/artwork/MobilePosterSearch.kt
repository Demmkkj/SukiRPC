package dev.nuvio.presence

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.Proxy
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// The existing Nuvio opt-in. Require both catalogs to respond and the combined matches to have a single IMDb ID.
internal object MobilePosterSearch {
    private val idPattern = Regex("tt[0-9]{7,12}")
    private val marks = Regex("\\p{M}+")
    private val spaces = Regex("\\s+")
    private val client = OkHttpClient.Builder().dns(ArtworkDns).proxy(Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(3500, TimeUnit.MILLISECONDS).readTimeout(3500, TimeUnit.MILLISECONDS)
        .callTimeout(5, TimeUnit.SECONDS).build()
    private const val MAX_JSON = 1024L * 1024

    private fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(marks, "").lowercase(Locale.ROOT).trim().replace(spaces, " ")

    suspend fun lookup(title: String): String {
        val clean = title.trim()
        if (clean.length !in 2..150 || clean.any { it.isISOControl() } || clean.contains("://")) return ""
        return try {
            val query = URLEncoder.encode(clean, StandardCharsets.UTF_8.name()).replace("+", "%20")
            val ids = linkedSetOf<String>()
            val normalized = normalize(clean)
            for (type in listOf("movie", "series")) {
                val url = "https://v3-cinemeta.strem.io/catalog/$type/top/search=$query.json"
                val items = fetch(url.toHttpUrl()).getJSONArray("metas")
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    if (normalize(item.optString("name")) != normalized) continue
                    item.optString("id").takeIf(idPattern::matches)?.let(ids::add)
                }
            }
            ids.singleOrNull()?.let { publicArtwork("https://images.metahub.space/poster/medium/$it/img") }.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }
    }

    private sealed interface Result {
        data class Redirect(val location: String) : Result
        data class Json(val data: JSONObject) : Result
    }

    private suspend fun fetch(initial: HttpUrl): JSONObject {
        var address = initial
        repeat(3) {
            when (val result = get(address)) {
                is Result.Json -> return result.data
                is Result.Redirect -> {
                    val next = requireNotNull(address.resolve(result.location))
                    require(next.isHttps && next.host in setOf("v3-cinemeta.strem.io", "cinemeta-live.strem.io") &&
                        next.port == 443 && next.encodedUsername.isEmpty() && next.encodedPassword.isEmpty() &&
                        next.encodedPath == initial.encodedPath && next.query == null && next.fragment == null)
                    address = next
                }
            }
        }
        throw IOException("Limite de redirecionamentos")
    }

    private suspend fun get(address: HttpUrl): Result = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(address).header("Accept", "application/json").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (response.isRedirect) return@use Result.Redirect(requireNotNull(response.header("Location")))
                        require(response.code == 200)
                        val body = requireNotNull(response.body)
                        val source = body.source()
                        require(!source.request(MAX_JSON + 1))
                        Result.Json(JSONObject(source.readUtf8()))
                    }
                }
                continuation.resumeWith(result)
            }
        })
    }
}
