package dev.nuvio.presence

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.io.IOException

/** ADB is used only for setup on the user's own, explicitly authorized Fire TV. */
internal object CollectorInstaller {
    private const val PACKAGE = "dev.nuvio.collector"
    private const val COMPONENT = "$PACKAGE/.CollectorService"
    private const val REMOTE = "/data/local/tmp/nuvio-metadata-collector.apk"
    private val lock = Any()

    private fun validHost(host: String): String {
        val p = host.trim().split('.').map { it.toIntOrNull() ?: -1 }
        require(p.size == 4 && p.all { it in 0..255 } &&
            (p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168))) {
            "Informe o IPv4 privado da sua Fire TV."
        }
        return p.joinToString(".")
    }

    private fun keys(context: Context): AdbKeyPair = synchronized(lock) {
        val dir = File(context.noBackupFilesDir, "collector-adb").apply { mkdirs() }
        val priv = File(dir, "adbkey")
        val pub = File(dir, "adbkey.pub")
        if (!priv.isFile || !pub.isFile) AdbKeyPair.generate(priv, pub)
        AdbKeyPair.read(priv, pub)
    }

    class SignatureMismatch : IOException("Assinatura diferente da versão instalada. Reinstalar apaga apenas os dados do Collector.")

    fun install(context: Context, host: String, port: Int, replace: Boolean, progress: (String) -> Unit) {
        val ip = validHost(host)
        require(port in 1..65535) { "Porta ADB inválida." }
        val file = File.createTempFile("collector-", ".apk", context.cacheDir)
        try {
            context.assets.open("collector.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
            progress("Conectando… aceite a autorização ADB na Fire TV, se aparecer.")
            Dadb.create(ip, port, keys(context)).use { adb ->
                fun command(cmd: String): String {
                    val result = adb.shell(cmd)
                    if (result.exitCode != 0) throw IOException(result.allOutput.trim().take(220).ifBlank { "Comando ADB falhou." })
                    return result.allOutput.trim()
                }
                val model = command("getprop ro.product.model")
                val manufacturer = command("getprop ro.product.manufacturer")
                require(model.startsWith("AFT") || manufacturer.equals("Amazon", true)) { "O dispositivo conectado não foi identificado como Fire TV." }
                progress("Fire TV $model conectada. Enviando Collector incorporado…")
                // Explicit mode avoids desktop-only unix file attribute lookup in DADB.
                adb.push(file, REMOTE, 420, file.lastModified())
                try {
                    fun tryInstall() = adb.shell("pm install -r $REMOTE")
                    var result = tryInstall()
                    if (result.exitCode != 0 || !result.allOutput.contains("Success")) {
                        if (result.allOutput.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE")) {
                            if (!replace) throw SignatureMismatch()
                            progress("Removendo a versão antiga do Collector (autorizado)…")
                            command("pm uninstall $PACKAGE")
                            result = tryInstall()
                        }
                        if (result.exitCode != 0 || !result.allOutput.contains("Success"))
                            throw IOException("Instalação falhou: " + result.allOutput.take(220))
                    }
                    if (!command("pm path $PACKAGE").contains("package:")) throw IOException("Collector não encontrado após instalação.")
                    progress("Collector instalado. Autorizando leitura de mídia…")
                    command("cmd notification allow_listener $COMPONENT 0")
                    val enabled = command("settings get secure enabled_notification_listeners")
                    if (!enabled.contains("$PACKAGE/.CollectorService") && !enabled.contains("$PACKAGE/$PACKAGE.CollectorService"))
                        throw IOException("A Fire TV não confirmou a autorização do Collector.")
                    command("am start -n $PACKAGE/.PairActivity")
                    progress("Instalado e autorizado. Na TV, gere o código de pareamento.")
                } finally {
                    runCatching { adb.shell("rm -f $REMOTE") }
                }
            }
        } finally {
            file.delete()
        }
    }
}
