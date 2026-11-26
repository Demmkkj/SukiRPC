package dev.nuvio.presence

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import java.net.InetAddress

private data class PairQrData(val ip: String, val identity: String, val code: String)

private fun decodePairQr(text: String): PairQrData {
    require(text.length <= 1024) { "QR Code inválido" }
    val j = JSONObject(text)
    require(j.getString("type") == "nuvio_pairing" && j.getInt("version") == 1) { "QR incompatível" }
    require(j.getInt("port") == 8766) { "Porta incompatível" }
    val ip = j.getString("host")
    require(ip.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) && ip.split('.').all { it.toInt() in 0..255 }) { "IPv4 inválido" }
    require(InetAddress.getByName(ip).isSiteLocalAddress) { "Use um IP privado" }
    val identity = j.getString("identity")
    require(identity.matches(Regex("[0-9a-f]{64}"))) { "Identidade inválida" }
    val code = j.getString("code")
    require(code.matches(Regex("[0-9]{8}"))) { "Código inválido" }
    return PairQrData(ip, identity, code)
}

@Composable
internal fun PairQrButton(
    onScanned: (ip: String, identity: String, code: String) -> Unit,
    onError: (String) -> Unit,
    enabled: Boolean = true
) {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) {
            val data = runCatching { decodePairQr(contents) }.getOrNull()
            if (data == null) onError("QR Code de pareamento inválido ou incompatível")
            else onScanned(data.ip, data.identity, data.code)
        }
    }
    OutlinedButton(enabled = enabled, onClick = {
        try {
            launcher.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Escaneie o QR Code do SukiRPC Collector")
                setBeepEnabled(false)
                setOrientationLocked(false)
            })
        } catch (_: Exception) {
            onError("Não foi possível abrir a câmera")
        }
    }) { Text("Escanear QR Code") }
}
