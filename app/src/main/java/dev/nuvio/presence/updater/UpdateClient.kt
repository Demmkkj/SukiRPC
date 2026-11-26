package dev.nuvio.presence.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.net.ssl.HttpsURLConnection

internal object UpdateClient {
    const val REPO = "Demmkkj/SukiRPC"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val MAX_APK = 150L * 1024 * 1024
    private val tagPattern = Regex("v[0-9]+\\.[0-9]+\\.[0-9]+-build\\.[0-9]+")
    private val hashPattern = Regex("[0-9a-f]{64}")
    private val abis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
    data class Release(val version: String, val code: Long, val abi: String, val url: String,
                       val sha: String, val size: Long, val notes: String)
    data class Check(val release: Release?, val message: String)

    @Suppress("DEPRECATION")
    private fun code(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
    @Suppress("DEPRECATION")
    private fun flags(): Int = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    private fun installed(c: Context) = c.packageManager.getPackageInfo(c.packageName, flags())
    fun installedVersion(c: Context): String = installed(c).versionName ?: "?"
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    @Suppress("DEPRECATION")
    private fun signers(p: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
        return signatures?.map { digest(it.toByteArray()) }?.toSet().orEmpty()
    }
    private fun allowed(url: URL, hosts: Set<String>) = url.protocol == "https" &&
        url.host.lowercase() in hosts && (url.port == -1 || url.port == 443) && url.userInfo == null
    private fun open(address: String, hosts: Set<String>): HttpsURLConnection {
        var url = URL(address)
        repeat(6) {
            if (!allowed(url, hosts)) throw IOException("Endereço de atualização não autorizado.")
            val c = url.openConnection() as HttpsURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 12000; c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "SukiRPC-Updater")
            c.setRequestProperty("Accept-Encoding", "identity")
            try {
                val status = c.responseCode
                if (status in listOf(301,302,303,307,308)) {
                    val next = c.getHeaderField("Location") ?: throw IOException("Redirecionamento inválido.")
                    url = URL(url, next); c.disconnect()
                } else if (status == 200) return c
                else throw IOException(if (status == 404) "Nenhuma release pública estável disponível." else "Servidor de atualizações: HTTP $status")
            } catch (e: Exception) { c.disconnect(); throw e }
        }
        throw IOException("Redirecionamentos demais.")
    }
    private val releaseHosts = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    private fun readSmall(address: String, hosts: Set<String>, limit: Int): JSONObject {
        val c = open(address, hosts)
        try {
            val bytes = c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192)
                while (true) { val n = input.read(buf); if (n < 0) break
                    if (out.size() + n > limit) throw IOException("Resposta excede o limite.")
                    out.write(buf, 0, n)
                }; out.toByteArray()
            }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally { c.disconnect() }
    }
    suspend fun check(ctx: Context): Check = withContext(Dispatchers.IO) {
        val current = installed(ctx)
        val release = readSmall(API, setOf("api.github.com"), 1024 * 1024)
        val tag = release.getString("tag_name")
        require(tagPattern.matches(tag) && !release.optBoolean("draft") && !release.optBoolean("prerelease")) { "Release inválida." }
        val base = "https://github.com/$REPO/releases/download/$tag/"
        val assets = release.getJSONArray("assets")
        val names = (0 until assets.length()).map { assets.getJSONObject(it).getString("name") }.toSet()
        require("update.json" in names) { "Release sem manifesto de atualização." }
        val meta = readSmall(base + "update.json", releaseHosts, 65536)
        val version = meta.getString("versionName"); val versionCode = meta.getLong("versionCode")
        require(meta.getInt("schema") == 1 && meta.getString("packageName") == ctx.packageName &&
            tag == "v$version-build.${meta.getLong("build")}" && versionCode > 0) { "Manifesto inválido." }
        if (versionCode <= code(current)) return@withContext Check(null, "Você está atualizado.")
        val files = meta.getJSONObject("files")
        val supported = Build.SUPPORTED_ABIS.filter { it in abis }
        val abi = (supported + "universal").firstOrNull { files.has(it) && "SukiRPC-$it.apk" in names }
            ?: throw IOException("Nenhum APK compatível.")
        val file = files.getJSONObject(abi); val sha = file.getString("sha256"); val size = file.getLong("size")
        require(hashPattern.matches(sha) && size in 1..MAX_APK) { "Arquivo de atualização inválido." }
        Check(Release(version, versionCode, abi, base + "SukiRPC-$abi.apk", sha, size, release.optString("body").take(3000)), "Nova versão disponível")
    }
    private fun verify(ctx: Context, file: File, release: Release) {
        require(file.isFile && file.length() == release.size) { "Tamanho do APK incorreto." }
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input -> val buf = ByteArray(65536); while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf,0,n) } }
        require(md.digest().hex() == release.sha) { "SHA-256 incorreto." }
        val pkg = ctx.packageManager.getPackageArchiveInfo(file.absolutePath, flags()) ?: throw IOException("APK inválido.")
        val current = installed(ctx); val expected = signers(current)
        require(pkg.packageName == ctx.packageName && code(pkg) == release.code && code(pkg) > code(current)) { "Pacote ou versão incompatível." }
        require(expected.isNotEmpty() && signers(pkg) == expected) { "Assinatura diferente da versão instalada." }
        ZipFile(file).use { zip ->
            val compatible = Build.SUPPORTED_ABIS.filter { it in abis }.any { abi ->
                listOf("libnuvio_rpc.so", "libdiscord_partner_sdk.so", "libc++_shared.so").all { zip.getEntry("lib/$abi/$it") != null }
            }
            require(compatible) { "Bibliotecas nativas incompatíveis." }
        }
    }
    suspend fun download(ctx: Context, release: Release, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "suki_updates"); require(dir.isDirectory || dir.mkdirs())
        val tmp = File.createTempFile("download-", ".apk", dir); val final = File(dir, "SukiRPC-${release.code}-${release.abi}.apk")
        try {
            val c = open(release.url, releaseHosts)
            try {
                c.inputStream.use { input -> tmp.outputStream().buffered().use { out ->
                    val buf = ByteArray(65536); var total = 0L; var last = -1
                    while (true) { ensureActive(); val n = input.read(buf); if (n < 0) break
                        total += n; require(total <= release.size && total <= MAX_APK) { "Download excede o tamanho previsto." }
                        out.write(buf,0,n); val pct = (total*100/release.size).toInt()
                        if (pct != last) { last = pct; withContext(Dispatchers.Main) { progress(pct) } }
                    }
                } }
            } finally { c.disconnect() }
            ensureActive(); verify(ctx, tmp, release); ensureActive()
            if (final.exists()) final.delete()
            require(tmp.renameTo(final)) { "Não foi possível preparar o APK." }

            dir.listFiles()?.filter { it != final && it.name.endsWith(".apk") }?.forEach { it.delete() }
            final
        } finally { tmp.delete() }
    }
    suspend fun installer(ctx: Context, file: File, release: Release): Intent = withContext(Dispatchers.IO) {
        verify(ctx,file,release)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    fun permissionIntent(ctx: Context) = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
}
