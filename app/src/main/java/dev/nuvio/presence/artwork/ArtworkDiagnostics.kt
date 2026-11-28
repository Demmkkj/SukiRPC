package dev.nuvio.presence

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.nuvio.artwork.ArtworkPolicy
import dev.nuvio.presence.ui.themes.ThemePanel
import kotlinx.coroutines.launch
import java.net.URI

@Composable
internal fun ArtworkDiagnostics(dashboard: Dashboard, options: Options) {
    val context = LocalContext.current
    val prefs = Settings.prefs(context)
    val privacy = readPrivacy(prefs)
    val playback = dashboard.playback
    val studio = dashboard.studio
    val permitted = playback != null && playback.artworkPermitted && artworkAllowed(options, privacy, playback.title)
    val public = when {
        studio != null && privacy.hidden.isEmpty() && !privacy.blocks(studio.name) -> studioPreviewUrl(studio.largeImage, true)
        permitted && playback != null -> playback.artwork.ifBlank { readFormat(prefs.all).fallback }
        else -> ""
    }
    val host = runCatching { URI(public).host }.getOrNull()
    val isMobile = playback?.mediaId?.startsWith("mobile:") == true
    val canSet = permitted && isMobile && playback?.let(::manualArtworkSupported) == true
    var input by remember(playback?.identity) { mutableStateOf("") }
    var message by remember(playback?.identity) { mutableStateOf("") }
    var probe by remember(public, playback?.identity) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    ThemePanel(Modifier.fillMaxWidth()) {
        Text("Diagnóstico de capas", style = MaterialTheme.typography.titleMedium)
        if (dashboard.artworkStatus.isNotBlank()) Text(dashboard.artworkStatus)
        else if (studio != null) Text("RPC Studio · imagem configurada no preset")
        else Text("Aguardando uma sessão de mídia")
        if (permitted && playback != null) {
            if (playback.artworkSignals.isNotBlank()) Text(playback.artworkSignals)
            playback.localArtwork?.let { Text("Bitmap em memória · ${it.size} bytes") }
        }
        if (host != null) Text("Servidor da imagem · $host")
        Text("Envio da imagem ao SDK · " + if (dashboard.artworkForwarded) "enviada" else "sem envio ativo")
        Text("Estado da presença · " + dashboard.discord)
        Text("A exibição da imagem é verificável no perfil do Discord.", style = MaterialTheme.typography.bodySmall)

        if (cachedPublicArtwork(public).isNotEmpty()) {
            OutlinedButton(enabled = probe != "Testando…", onClick = {
                probe = "Testando…"
                scope.launch { probe = ArtworkImages.probe(context, public) }
            }) { Text("Testar download da capa") }
            if (probe.isNotEmpty()) Text(probe)
        }
        if (canSet && playback != null) {
            val youtube = ArtworkPolicy.isYoutubeSource(playback.appId)
            Text("Capa para ${playback.title.take(160)}", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(input, { input = it.take(768); message = "" },
                label = { Text(if (youtube) "Link ou ID do vídeo atual" else "URL pública de capa ou ID IMDb") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = input.isNotBlank(), onClick = {
                    val current = Monitor.state.value.playback
                    message = if (current != null && current.identity == playback.identity && current.artworkPermitted &&
                        artworkAllowed(Settings.options(context), readPrivacy(prefs), current.title) &&
                        Integrations(context).artwork(current.appId) && Monitor.manualArtwork.set(current, input)) {
                        input = ""
                        "Capa selecionada para a próxima sincronização desta mídia."
                    } else "Confira a mídia ativa e use um link ou ID compatível."
                }) { Text("Usar nesta sessão") }
                if (playback.artworkSource == "manual") TextButton(onClick = {
                    Monitor.manualArtwork.clear()
                    message = "Voltando à capa automática na próxima sincronização."
                }) { Text("Automática") }
            }
            if (message.isNotBlank()) Text(message)
        }
    }
}
