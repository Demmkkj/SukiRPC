package dev.nuvio.presence.ui.renaissance

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import dev.nuvio.presence.ui.genesis.GenesisItemHeight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable internal fun RenaissanceSettings(
    version:String,interval:Int,onInterval:(Int)->Unit,
    onPresence:()->Unit,onThemes:()->Unit,onUpdates:()->Unit,onDiagnostics:()->Unit,onAbout:()->Unit
){
    SectionLabel("Personalização")
    GenesisSettingsRow("Aparência", "Temas e qualidade gráfica", onThemes)
    GenesisSettingsRow("Rich Presence", "Formato e privacidade", onPresence)
    SectionLabel("Aplicativo")
    GenesisSettingsRow("Atualizações", "", onUpdates)
    GenesisSettingsRow("Sobre o SukiRPC", version, onAbout)
    var advanced by rememberSaveable { mutableStateOf(false) }
    ClarityDrawer("Avançado", advanced, { advanced = !advanced }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Intervalo de sincronização", style = MaterialTheme.typography.titleMedium)
            Text("$interval s", fontFamily = Mono, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold)
        }
        Slider(value = interval.toFloat(), onValueChange = { onInterval(it.toInt().coerceIn(1, 5)) },
            valueRange = 1f..5f, steps = 3)
        Text("O intervalo aumenta durante pausas e períodos sem atividade.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GenesisSettingsRow("Diagnóstico", "", onDiagnostics)
    }
}

@Composable
private fun GenesisSettingsRow(label: String, summary: String, action: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = GenesisItemHeight)
        .clip(MaterialTheme.shapes.medium).clickable(onClick = action).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (summary.isNotBlank()) Text(summary, color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = colors.primary)
    }
}
