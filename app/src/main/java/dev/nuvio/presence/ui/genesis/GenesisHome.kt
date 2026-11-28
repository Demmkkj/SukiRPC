package dev.nuvio.presence.ui.genesis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nuvio.presence.*
import dev.nuvio.presence.ui.renaissance.Mono
import dev.nuvio.presence.ui.renaissance.SignalLabel
import dev.nuvio.presence.ui.themes.*

@Composable
internal fun GenesisHome(
    dashboard: Dashboard, now: Long, options: Options, ready: Boolean, paired: Boolean,
    onDevices: () -> Unit, onStudio: () -> Unit, onStop: () -> Unit, onStart: (String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { Settings.prefs(context) }
    val privacy = readPrivacy(prefs)
    val format = readFormat(prefs.all)
    val running = dashboard.running
    val stopping = dashboard.connection == "Encerrando"
    val confirmed = running && dashboard.discord == "Presença confirmada"
    val playback = dashboard.playback
    val origin = dashboard.connection.substringBefore(" •")
    val media = if (running) mediaPresence(playback, options, format, privacy, origin, now, System.currentTimeMillis()) else null
    val custom = dashboard.studio?.takeIf { running && privacy.hidden.isEmpty() && !privacy.blocks(it.name) }
    val category = remember(origin, playback?.episode, playback?.mediaId, custom?.type) {
        if (custom != null) {
            if (custom.type == "playing") "Jogos" else "Custom RPC"
        } else chronicleCategory(HistoryMedia("", "", "", origin, "", when {
            playback?.mediaId?.contains("movie", true) == true -> "Filme"
            playback?.episode?.isNotBlank() == true && origin.startsWith("Nuvio") -> "Episódio"
            else -> ""
        }))
    }
    var source by rememberSaveable { mutableStateOf(if (paired) "tv" else "auto") }

    SignalLabel(when {
        stopping -> "Encerrando"
        confirmed -> "Sincronizado"
        running -> "Sincronização ativa"
        ready -> "Pronto para sincronizar"
        else -> "Discord indisponível"
    }, confirmed)

    if (media != null || custom != null) {
        val artwork = media?.art ?: custom?.largeImage?.let { studioPreviewUrl(it, false) }.orEmpty()
        val local = playback?.localArtwork?.takeIf {
            media != null && playback.artworkPermitted && artworkAllowed(options, privacy, playback.title)
        }
        val title = media?.title?.ifBlank { "Atividade privada" } ?: custom?.name.orEmpty()
        val detail = media?.detail ?: custom?.details.orEmpty()
        val tall = category in listOf("Séries e filmes", "Jogos")
        if (category == "Vídeos") GenesisArtwork(artwork, Modifier.fillMaxWidth().aspectRatio(16f / 9f), local = local, horizon = custom == null || cachedPublicArtwork(artwork).isNotEmpty())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (category != "Vídeos") GenesisArtwork(artwork, Modifier.width(if (tall) 112.dp else 126.dp)
                .aspectRatio(if (tall) 2f / 3f else 1f), local = local, horizon = custom == null || cachedPublicArtwork(artwork).isNotEmpty())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GenesisGap)) {
                Text(if (custom != null) "RPC Studio" else origin,
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.headlineMedium,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) Text(detail, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (media != null && playback != null && options.timestamps &&
            Hidden.Progress !in privacy.hidden && format.clock != ClockDisplay.Off &&
            playback.duration > 0 && playback.position >= 0
        ) {
            val position = playback.positionAt(now)
            LinearProgressIndicator(progress = { (position.toDouble() / playback.duration).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth())
            Text("${timestamp(position)} / ${timestamp(playback.duration)}", fontFamily = Mono,
                style = MaterialTheme.typography.labelSmall)
        }
        if (custom != null) StudioPreview(custom, null, null)
        else if (media != null) GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Prévia no Discord", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(GenesisGap),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (artwork.isNotBlank()) GenesisArtwork(artwork, Modifier.size(54.dp))
                    Column(Modifier.weight(1f)) {
                        Text(listOf(if (media.watching) "Assistindo" else "Jogando", media.activityName)
                            .filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.titleSmall)
                        if (media.title.isNotBlank()) Text(media.title, style = MaterialTheme.typography.bodyMedium)
                        if (media.detail.isNotBlank()) Text(media.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    } else {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (running) "Aguardando atividade" else "Nenhuma presença ativa",
                    style = MaterialTheme.typography.headlineMedium)
                if (!running && ready) Text("Escolha a fonte para começar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (running) {
        if (!confirmed) Text(dashboard.discord.substringBefore(" •"), style = MaterialTheme.typography.bodySmall)
        GlassOutlinedButton(onClick = onStop, enabled = !stopping, modifier = Modifier.fillMaxWidth()) {
            Text(if (stopping) "Encerrando…" else "Parar sincronização")
        }
    } else {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("auto" to "Automático", "mobile" to "Celular", "tv" to "TV").forEach { (id, label) ->
                FilterChip(selected = source == id, onClick = { source = id }, label = { Text(label) })
            }
        }
        GlassButton(onClick = { onStart(source) }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text(if (source == "tv" && !paired) "Parear TV" else "Sincronizar")
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GenesisGap)) {
        TextButton(onClick = onDevices, modifier = Modifier.weight(1f)) { Text("Dispositivos") }
        TextButton(onClick = onStudio, modifier = Modifier.weight(1f)) { Text("RPC Studio") }
    }
}
