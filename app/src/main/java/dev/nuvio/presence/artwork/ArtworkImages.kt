package dev.nuvio.presence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.request.ErrorResult
import java.io.File
import java.io.IOException
import java.net.Proxy
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okio.ForwardingSource
import okio.buffer

internal object ArtworkImages {
    private const val MAX_DOWNLOAD = 6L * 1024 * 1024
    @Volatile private var instance: ImageLoader? = null

    fun loader(context: Context): ImageLoader = instance ?: synchronized(this) {
        instance ?: build(context.applicationContext).also { instance = it }
    }

    private fun build(context: Context): ImageLoader {
        val client = OkHttpClient.Builder().dns(ArtworkDns).proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
            .addInterceptor { chain ->
                val request = chain.request()
                if (cachedPublicArtwork(request.url.toString()).isEmpty()) throw IOException("URL de capa rejeitada")
                val response = chain.proceed(request)
                if (response.isRedirect) {
                    response.close()
                    throw IOException("Redirecionamento de capa rejeitado")
                }
                val body = response.body
                if (!response.isSuccessful || body == null) response
                else {
                    if (body.contentLength() > MAX_DOWNLOAD || body.contentType()?.type?.let { it != "image" } == true) {
                        response.close()
                        throw IOException("Resposta de imagem inválida")
                    }
                    val limited = object : ResponseBody() {
                        private val input = object : ForwardingSource(body.source()) {
                            private var count = 0L
                            override fun read(sink: okio.Buffer, byteCount: Long): Long {
                                val received = super.read(sink, byteCount)
                                if (received > 0) {
                                    count += received
                                    if (count > MAX_DOWNLOAD) throw IOException("Imagem excede o limite")
                                }
                                return received
                            }
                        }.buffer()
                        override fun contentLength() = body.contentLength()
                        override fun contentType() = body.contentType()
                        override fun source() = input
                    }
                    response.newBuilder().body(limited).build()
                }
            }.build()
        return ImageLoader.Builder(context).okHttpClient(client)
            .memoryCache { MemoryCache.Builder(context).maxSizeBytes(4 * 1024 * 1024).build() }
            .diskCache { DiskCache.Builder().directory(File(context.cacheDir, "horizon-artwork"))
                .maxSizeBytes(12L * 1024 * 1024).build() }
            .build()
    }

    suspend fun probe(context: Context, url: String): String {
        val image = cachedPublicArtwork(url)
        if (image.isEmpty()) return "URL reprovada pela política de capas"
        val request = ImageRequest.Builder(context).data(image).size(256, 256)
            .diskCachePolicy(CachePolicy.DISABLED).memoryCachePolicy(CachePolicy.DISABLED).build()
        return when (val result = loader(context).execute(request)) {
            is SuccessResult -> "Download e decodificação OK no aparelho"
            is ErrorResult -> when (result.throwable) {
                is java.net.UnknownHostException -> "Falha de DNS ou endereço público indisponível"
                is java.net.SocketTimeoutException -> "Tempo de download excedido"
                is javax.net.ssl.SSLException -> "Falha de TLS no download"
                else -> when (result.throwable.message) {
                    "Redirecionamento de capa rejeitado" -> "Redirecionamento bloqueado"
                    "Resposta de imagem inválida", "Imagem excede o limite" -> "Resposta inválida ou acima do limite"
                    else -> "Falha ao baixar ou decodificar a imagem"
                }
            }
        }
    }

    suspend fun bitmap(context: Context, url: String, local: ByteArray?, width: Int, height: Int): Bitmap? {
        val data: Any = when {
            local != null -> local
            url == PRISM_LOGO -> R.drawable.suki_icon_foreground
            cachedPublicArtwork(url).isNotEmpty() -> url
            else -> return null
        }
        val request = ImageRequest.Builder(context).data(data).size(width, height).allowHardware(false)
            .apply { if (local != null) diskCachePolicy(CachePolicy.DISABLED) }.build()
        return ((loader(context).execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
    }
}
