package dev.nuvio.presence

import android.content.SharedPreferences
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.nuvio.presence.ui.genesis.GenesisArtwork
import dev.nuvio.presence.ui.themes.ThemePanel
import kotlinx.coroutines.delay

@Composable private fun <T> Choice(label: String, selected: T, items: List<T>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label · $selected") }
        DropdownMenu(expanded, { expanded = false }) {
            items.forEach { item -> DropdownMenuItem(text = { Text(item.toString()) }, onClick = {
                onSelect(item)
                expanded = false
            }) }
        }
    }
}

@Composable internal fun PrismEditor(prefs: SharedPreferences, playback: Playback?, device: String, now: Long, options: Options) {
    var applied by remember { mutableStateOf(readFormat(prefs.all)) }
    var draft by remember { mutableStateOf(applied) }
    var privacy by remember { mutableStateOf(readPrivacy(prefs)) }
    DisposableEffect(prefs){
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="prism.hidden"||key=="prism.excluded")privacy=readPrivacy(prefs)}
        prefs.registerOnSharedPreferenceChangeListener(listener);onDispose{prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    var previewFormat by remember { mutableStateOf(draft) }
    LaunchedEffect(draft) {
        if (draft.preset == MediaPreset.Custom) delay(150)
        previewFormat = draft.copy(fallback = safeFallback(draft.fallback))
    }
    fun protect(next: MediaPrivacy) {
        privacy = next
        next.save(prefs)
    }
    val demo = remember { Playback("Horizonte de Vidro", "S1:E3 — O encontro", "", "", "playing",
        372000, 1440000, 1.0, SystemClock.elapsedRealtime(), "2026", "Animação") }
    val sample = playback ?: demo
    val preview = mediaPresence(sample, options, previewFormat, privacy, device, now, System.currentTimeMillis())
    ThemePanel(Modifier.fillMaxWidth()) {
        Text(if (playback == null) "Prévia · demonstração local" else "Prévia · sessão atual", style = MaterialTheme.typography.labelMedium)
        if (preview == null) Text("Presença oculta ou inativa") else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (preview.art == PRISM_LOGO || (playback == null && options.artwork &&
                    privacy.hidden.intersect(setOf(Hidden.Title, Hidden.Artwork, Hidden.Spoilers)).isEmpty() && preview.art.isEmpty()))
                Image(painterResource(R.drawable.suki_icon_foreground), "Logotipo", Modifier.size(60.dp))
            else if (preview.art.isNotBlank()) GenesisArtwork(preview.art, Modifier.size(60.dp))
            Column(Modifier.weight(1f)) {
                Text(preview.activityName, style = MaterialTheme.typography.titleMedium)
                if (preview.title.isNotBlank()) Text(preview.title)
                if (preview.detail.isNotBlank()) Text(preview.detail)
                if (preview.end > 0 && previewFormat.clock != ClockDisplay.Elapsed)
                    Text("${timestamp((sample.duration - preview.anchorPosition).coerceAtLeast(0))} restantes")
                else if (preview.start > 0) Text("${timestamp(preview.anchorPosition)} decorridos")
            }
        }
    }
    ThemePanel(Modifier.fillMaxWidth()) {
        Choice("Preset", draft.preset, MediaPreset.entries) { draft = draft.copy(preset = it) }
        if (draft.preset == MediaPreset.Custom) {
            Text("Variáveis: {title}, {subtitle}, {season}, {episode}, {year}, {genre}, {device}, {status}. Separe os campos opcionais com ·.")
            OutlinedTextField(draft.title, { draft = draft.copy(title = it.take(512)) }, label = { Text("Título principal") })
            OutlinedTextField(draft.detail, { draft = draft.copy(detail = it.take(512)) }, label = { Text("Descrição") })
        }
        Choice("Tempo", draft.clock, ClockDisplay.entries) { draft = draft.copy(clock = it) }
        OutlinedTextField(draft.fallback, { draft = draft.copy(fallback = it.take(512)) },
            label = { Text("Capa alternativa (URL de pôster aceita)") }, singleLine = true)
        TextButton(onClick = { draft = draft.copy(fallback = PRISM_LOGO) }) { Text("Usar logo SukiRPC como alternativa") }
        val valid = draft.fallback.isBlank() || safeFallback(draft.fallback).isNotEmpty()
        if (!valid) Text("Use uma URL de pôster TMDB ou MetaHub sem parâmetros.")
        Button(onClick = { applied = draft; draft.save(prefs) }, enabled = valid && draft != applied) { Text("Aplicar formato") }
        TextButton(onClick = { draft = MediaFormat() }) { Text("Restaurar formato padrão") }
    }
    ThemePanel(Modifier.fillMaxWidth()) {
        Text("Privacidade", style = MaterialTheme.typography.titleMedium)
        Text("Aplicação imediata. O RPC Studio pausa enquanto houver um filtro de privacidade ativo.")
        val labels = listOf("Ocultar título e dados que identificam a obra", "Ocultar capa", "Ocultar temporada e episódio",
            "Ocultar progresso", "Ocultar spoilers (episódio e capa)", "Suspender presença até reativar")
        Hidden.entries.forEachIndexed { index, field ->
            SettingToggle(labels[index], field in privacy.hidden) { enabled ->
                protect(privacy.copy(hidden = if (enabled) privacy.hidden + field else privacy.hidden - field))
            }
        }
        var entry by remember { mutableStateOf("") }
        OutlinedTextField(entry, { entry = it.take(512) }, singleLine = true, label = { Text("Título para excluir") })
        TextButton(onClick = { protect(privacy.copy(excluded = privacy.excluded + entry.trim())); entry = "" },
            enabled = entry.isNotBlank()) { Text("Adicionar à lista de exclusão") }
        privacy.excluded.sorted().forEach { name ->
            TextButton(onClick = { protect(privacy.copy(excluded = privacy.excluded - name)) }) { Text("Remover · $name") }
        }
    }
}
