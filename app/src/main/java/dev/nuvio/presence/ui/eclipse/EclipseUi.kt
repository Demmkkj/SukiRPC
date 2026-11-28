package dev.nuvio.presence.ui.eclipse

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.nuvio.presence.*

private val Black = Color(0xFF08080A)
private val Line = Color(0xFF29252F)

@Composable
internal fun EclipseTheme(colorScheme: ColorScheme, typography: Typography = Typography(),
                          shapes: Shapes = Shapes(), content: @Composable () -> Unit) {
    val scheme = if (colorScheme.background.luminance() < .35f) {
        colorScheme.copy(background = if (colorScheme.background.luminance() < .004f) Color.Black else Black, surface = Color(0xFF141218),
            surfaceVariant = Color(0xFF211D28), onBackground = Color(0xFFF3EFF6),
            onSurface = Color(0xFFF3EFF6), onSurfaceVariant = Color(0xFFA29AAE),
            outline = Color(0xFF51465F), outlineVariant = Line)
    } else colorScheme
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}

@Composable
internal fun EclipsePage(tab: Int, content: @Composable (Int) -> Unit) {
    val states = rememberSaveableStateHolder()
    Crossfade(targetState = tab, animationSpec = tween(if(android.animation.ValueAnimator.areAnimatorsEnabled())180 else 0), label = "eclipse_page") { page ->
        states.SaveableStateProvider(page) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content(page)
            }
        }
    }
}

@Composable
internal fun EclipseHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SpringButton(label: String, enabled: Boolean = true, filled: Boolean = true, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) .965f else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium), label = "press")
    val haptic = LocalHapticFeedback.current
    val action = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
    val modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).graphicsLayer { scaleX = scale; scaleY = scale }
    if (filled) Button(onClick = action, enabled = enabled, modifier = modifier,
        interactionSource = interactions, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(label) }
    else OutlinedButton(onClick = action, enabled = enabled, modifier = modifier,
        interactionSource = interactions, shape = RoundedCornerShape(16.dp)) { Text(label) }
}

@Composable
internal fun EclipseSettingsLink(title: String, summary: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                if(summary.isNotBlank())Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun EclipseNavigation(tab: Int, onTab: (Int) -> Unit) {
    val current = if (tab == 2 || tab == 4) 3 else tab
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Início", 1 to "Dispositivos", 5 to "RPC Studio", 3 to "Ajustes").forEach { (id, name) ->
                    val color by animateColorForNav(current == id)
                    val selected = current == id
                    Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                        .background(color).clickable(role = Role.Tab) { onTab(id) }, contentAlignment = Alignment.Center) {
                        Text(name, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun animateColorForNav(selected: Boolean): State<Color> = androidx.compose.animation.animateColorAsState(
    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .13f) else Color.Transparent,
    animationSpec = tween(180), label = "nav_color")

@Composable
private fun MoonMark(size: Int) {
    Image(painterResource(dev.nuvio.presence.R.drawable.suki_icon_foreground), contentDescription = null,
        modifier = Modifier.size(size.dp))
}

@Composable
private fun Artwork(url: String, visible: Boolean) {
    val context = LocalContext.current
    val request = remember(url, context) {
        ImageRequest.Builder(context).data(url).size(320, 480).crossfade(250).build()
    }
    Box(Modifier.size(width = 88.dp, height = 126.dp).clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        MoonMark(76)
        if (visible && url.isNotBlank()) {
            AsyncImage(model = request, contentDescription = "Capa da reprodução", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun EclipseHome(playback: Playback?, now: Long, options: Options, running: Boolean,
    connection: String, discord: String, ready: Boolean, paired: Boolean,
    onDevices: () -> Unit, onStop: () -> Unit, onStart: (String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(if (paired) "tv" else "mobile") }
    val p = playback?.takeIf { it.valid }
    val context = LocalContext.current
    val version = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    val status = if (running) connection else if (ready) "Pronto para sincronizar" else "SDK indisponível"
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SUKIRPC / ECLIPSE", color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            Text(version, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(100.dp).border(1.dp, accent.copy(alpha = .35f), CircleShape), contentAlignment = Alignment.Center) {
                MoonMark(104)
            }
            Crossfade(targetState = running, animationSpec = tween(200), label = "session") { active ->
                Text(if (active) "Sincronização ativa" else "Sua presença, do seu jeito.",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (running) Color(0xFF9BD7AD) else colors.onSurfaceVariant))
                Text(status, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        if (p == null) {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nenhuma reprodução", fontWeight = FontWeight.Medium)
                Text(if (running) "Abra o Nuvio e comece a assistir." else "Escolha uma fonte para começar.",
                    color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(p.artwork, options.artwork)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (p.state == "paused") "PAUSADO" else if (p.state == "playing") "ASSISTINDO AGORA" else "REPRODUÇÃO", color = accent, style = MaterialTheme.typography.labelSmall)
                    Text(p.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (options.episode && p.episode.isNotBlank()) Text(p.episode, color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (options.timestamps) Text(timestamp(p.positionAt(now)), color = accent, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (options.timestamps && p.duration > 0 && p.position >= 0) {
                val fraction = (p.positionAt(now).toDouble() / p.duration.toDouble()).coerceIn(0.0, 1.0).toFloat()
                // Animation is local to the progress element, not the entire screen.
                key(p.identity) { EclipseProgress(fraction, timestamp(p.positionAt(now)), timestamp(p.duration)) }
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Discord", fontWeight = FontWeight.Medium)
                Text(discord, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Text(if (ready) "SDK OK" else "Offline", style = MaterialTheme.typography.labelSmall, color = accent)
        }
        val preview = presenceFor(p, options, now, System.currentTimeMillis())
        if (preview != null && running) {
            Surface(color = colors.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("PRÉVIA DA PRESENÇA", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text(preview.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(preview.detail, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text("FONTE DE REPRODUÇÃO", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, letterSpacing = 1.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("mobile" to "Mobile", "tv" to "TV", "auto" to "Auto").forEach { (id, label) ->
                FilterChip(selected = mode == id, enabled = !running, onClick = { mode = id },
                    label = { Text(label, maxLines = 1) }, modifier = Modifier.weight(1f))
            }
        }
        if (!running && mode == "tv" && !paired) Text("Pareie sua TV antes de sincronizar.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        SpringButton(if (running) "Encerrar sincronização" else if (mode == "tv" && !paired) "Configurar TV" else "Iniciar sincronização",
            enabled = running || ready, filled = !running) { if (running) onStop() else onStart(mode) }
        TextButton(onClick = onDevices, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Gerenciar dispositivos") }
        Text("One presence. Anywhere.", Modifier.fillMaxWidth(), color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EclipseProgress(fraction: Float, elapsed: String, duration: String) {
    val animated by animateFloatAsState(fraction, tween(650), label = "progress")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
        Row(Modifier.fillMaxWidth()) {
            Text(elapsed, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(duration, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}
