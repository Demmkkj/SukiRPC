package dev.nuvio.presence.updater

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun UpdateCenter(ctx: Context, visible: Boolean, onVisible: (Boolean) -> Unit) {
    val prefs = remember { ctx.getSharedPreferences("suki_updates", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(prefs.getBoolean("auto", false)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Verifique se há uma nova versão estável.") }
    var release by remember { mutableStateOf<UpdateClient.Release?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var progress by remember { mutableIntStateOf(-1) }
    suspend fun check(automatic: Boolean) {
        if (busy) return
        busy = true; status = "Consultando releases…"; release = null; file = null; progress = -1
        try {
            val result = UpdateClient.check(ctx)
            release = result.release; status = result.message
            if (automatic && result.release != null) onVisible(true)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status = e.message?.take(180) ?: "Não foi possível verificar." }
        finally { busy = false }
    }
    LaunchedEffect(Unit) { if (auto) check(true) }
    if (visible) AlertDialog(
        onDismissRequest = { if (!busy) onVisible(false) },
        title = { Text("SukiRPC · Atualizações") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Versão instalada: ${remember { UpdateClient.installedVersion(ctx) }}")
            Text(status, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Verificar ao abrir", Modifier.weight(1f)); Switch(checked = auto, onCheckedChange = { auto = it; prefs.edit().putBoolean("auto", it).apply() })
            }
            Text("Consulta apenas o repositório público de releases. Nada da sua reprodução é enviado.", style = MaterialTheme.typography.bodySmall)
            release?.let { r ->
                HorizontalDivider(); Text("Nova versão: ${r.version}"); Text("Arquitetura: ${r.abi}")
                Text("Download: ${r.size / (1024*1024)} MB")
                if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodySmall)
            }
            if (busy && progress >= 0) { LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()); Text("$progress%") }
            else if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (file != null) Text("SHA-256, pacote, versão e certificado conferidos. O Android fará a verificação final.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            scope.launch {
                val r = release; val f = file
                if (r == null) check(false)
                else if (f == null) {
                    busy = true; progress = 0; status = "Baixando APK…"
                    try { file = UpdateClient.download(ctx,r) { progress = it }; status = "APK verificado e pronto." }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { status = e.message?.take(180) ?: "Falha no download." }
                    finally { busy = false; progress = -1 }
                } else {
                    busy = true
                    try {
                        if (!ctx.packageManager.canRequestPackageInstalls()) {
                            status = "O Android precisa autorizar a instalação. Retorne aqui depois, se permitido."
                            ctx.startActivity(UpdateClient.permissionIntent(ctx))
                        } else ctx.startActivity(UpdateClient.installer(ctx,f,r))
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { status = e.message?.take(180) ?: "Instalação indisponível."; file = null }
                    finally { busy = false }
                }
            }
        }) { Text(if (file != null) "Instalar" else if (release != null) "Baixar" else "Verificar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { onVisible(false) }) { Text("Fechar") } }
    )
}
