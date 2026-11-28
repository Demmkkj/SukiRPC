package dev.nuvio.presence.ui.genesis

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.nuvio.presence.R
import dev.nuvio.presence.ui.renaissance.parentRoute
import dev.nuvio.presence.ui.renaissance.primaryRoute
import dev.nuvio.presence.ui.themes.*

internal val GenesisGap = 12.dp
internal val GenesisItemHeight = 48.dp

internal fun genesisMotion(millis: Int): Int =
    if (ValueAnimator.areAnimatorsEnabled()) millis else 0

private val destinations = listOf(
    Triple(0, "Início", Icons.Filled.Home),
    Triple(9, "Extensões", Icons.Filled.List),
    Triple(5, "RPC Studio", Icons.Filled.AddCircle),
    Triple(8, "Chronicle", Icons.Filled.DateRange),
    Triple(3, "Ajustes", Icons.Filled.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GenesisShell(route: Int, navigate: (Int) -> Unit, content: @Composable (Int) -> Unit) {
    val theme = LocalThemeId.current
    val quality = LocalGraphics.current
    val colors = MaterialTheme.colorScheme
    val blur = if (useThemeBlur(theme, quality)) remember(theme) { HazeState() } else null
    val parent = parentRoute(route)
    val current = primaryRoute(route)
    val states = rememberSaveableStateHolder()
    BackHandler(enabled = parent != null) { parent?.let(navigate) }

    CompositionLocalProvider(LocalUltraBackdrop provides blur) {
        Box(Modifier.fillMaxSize()) {
            val backdrop = if (blur == null) Modifier else Modifier.hazeSource(blur)
            Box(backdrop.fillMaxSize()) {
                ThemeBackdrop()
                LocalSkin.current?.let { ThemeWallpaper(LocalBackground.current, it, quality) }
            }
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = colors.onBackground,
                topBar = {
                    TopAppBar(
                        title = {
                            if (route == 0) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Image(painterResource(R.drawable.suki_eclipse_notification), null,
                                        Modifier.size(26.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.primary))
                                    Text("SukiRPC", style = MaterialTheme.typography.titleLarge)
                                }
                            } else Text(
                                destinations.firstOrNull { it.first == route }?.second ?: pageName(route),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            if (parent != null) IconButton(onClick = { navigate(parent) }) {
                                Icon(Icons.Filled.ArrowBack, "Voltar")
                            }
                        },
                        actions = {
                            if (route == 9) TextButton(onClick = { navigate(1) }) { Text("Dispositivos") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent,
                            titleContentColor = colors.onBackground, navigationIconContentColor = colors.onBackground)
                    )
                },
                bottomBar = {
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center) {
                        val shape = MaterialTheme.shapes.large
                        Surface(
                            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                                .ultraGlass(shape, colors.surface.copy(alpha = .86f), strong = true),
                            shape = shape,
                            color = if (blur == null) colors.surface else Color.Transparent,
                            shadowElevation = if (quality == GraphicsMode.ECO) 0.dp else 4.dp
                        ) {
                            Row(Modifier.selectableGroup().padding(horizontal = 4.dp, vertical = 3.dp)) {
                                destinations.forEach { (destination, label, icon) ->
                                    val selected = current == destination
                                    val ink = if (selected) colors.primary else colors.onSurfaceVariant
                                    Column(
                                        Modifier.weight(1f).heightIn(min = GenesisItemHeight + 10.dp)
                                            .clip(shape).selectable(selected, role = Role.Tab, onClick = { navigate(destination) })
                                            .padding(vertical = 4.dp, horizontal = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(color = if (selected) colors.primary.copy(alpha = .14f) else Color.Transparent,
                                            contentColor = ink, shape = CircleShape) {
                                            Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                                                Icon(icon, null, Modifier.size(20.dp))
                                            }
                                        }
                                        Text(label, color = ink, textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.labelSmall, maxLines = 2,
                                            overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                    Crossfade(route, modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                        animationSpec = tween(genesisMotion(180)), label = "Genesis navigation") { page ->
                        states.SaveableStateProvider(page) { content(page) }
                    }
                }
            }
        }
    }
}
