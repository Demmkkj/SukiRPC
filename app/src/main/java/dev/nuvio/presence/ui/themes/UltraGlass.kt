package dev.nuvio.presence.ui.themes

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

internal val LocalUltraBackdrop = staticCompositionLocalOf<HazeState?> { null }

// Android 12 (API 31) has known RenderNode invalidation issues in this Haze release.
internal fun supportsLiveBlur(id: ThemeId, mode: GraphicsMode, sdk: Int = Build.VERSION.SDK_INT): Boolean =
    sdk >= 32 && mode == GraphicsMode.ULTRA && (id.isGenesis() || id == ThemeId.LIQUID || id == ThemeId.AERO || id == ThemeId.PHANTOM)

@Composable internal fun useThemeBlur(id:ThemeId,mode:GraphicsMode):Boolean =
    LocalSkin.current?.let{allowGlass(mode==GraphicsMode.ULTRA,Build.VERSION.SDK_INT,it.numbers.getValue("blur"))}?:supportsLiveBlur(id,mode)
@Composable internal fun liveBlurActive(): Boolean =
    LocalUltraBackdrop.current != null && useThemeBlur(LocalThemeId.current, LocalGraphics.current)

@Composable internal fun Modifier.ultraGlass(shape: Shape, tint: Color, strong: Boolean = false): Modifier {
    val state = LocalUltraBackdrop.current
    if (state == null || !liveBlurActive()) return this
    val id = LocalThemeId.current
    val radius = LocalSkin.current?.numbers?.get("blur")?.dp ?: when (id) {
        ThemeId.GENESIS, ThemeId.GENESIS_LIGHT -> if (strong) 18.dp else 12.dp
        ThemeId.LIQUID -> if (strong) 24.dp else 16.dp
        ThemeId.AERO -> if (strong) 30.dp else 22.dp
        else -> if (strong) 28.dp else 20.dp
    }
    val bg = MaterialTheme.colorScheme.background
    val style = remember(bg, tint, radius) {
        HazeStyle(backgroundColor = bg, tint = HazeTint(tint), blurRadius = radius,
            noiseFactor = 0f, fallbackTint = HazeTint(tint.copy(alpha = .94f)))
    }
    return clip(shape).hazeEffect(state = state, style = style)
}

// Material buttons retain ripple, semantics, touch targets and disabled states.
@Composable internal fun GlassButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    if(LocalSkin.current?.styles?.get("button")=="Outlined"){GlassOutlinedButton(onClick,modifier,enabled,content);return}
    if (!liveBlurActive()) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled, shape=MaterialTheme.shapes.medium, content = content)
        return
    }
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val tint = colors.primary.copy(alpha = if (enabled) .78f else .23f)
    Button(onClick = onClick, modifier = modifier.ultraGlass(shape, tint, strong = true)
        .border(1.dp, Color.White.copy(alpha = if (enabled) .52f else .18f), shape),
        enabled = enabled, shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
            contentColor = colors.onPrimary, disabledContainerColor = Color.Transparent,
            disabledContentColor = colors.onSurface.copy(alpha = .58f)), content = content)
}

@Composable internal fun GlassOutlinedButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    if (!liveBlurActive()) {
        if((LocalThemeId.current.isGenesis() || LocalThemeId.current==ThemeId.RENAISSANCE) && LocalSkin.current==null){
            val colors=MaterialTheme.colorScheme
            FilledTonalButton(onClick=onClick,modifier=modifier,enabled=enabled,shape=MaterialTheme.shapes.medium,
                colors=ButtonDefaults.filledTonalButtonColors(containerColor=colors.primary.copy(alpha=.13f),contentColor=colors.primary),content=content)
        }else OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, shape=MaterialTheme.shapes.medium, content = content)
        return
    }
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    OutlinedButton(onClick = onClick, modifier = modifier.ultraGlass(shape,
        colors.surface.copy(alpha = if (enabled) .52f else .82f)), enabled = enabled, shape = shape,
        border = BorderStroke(1.dp, colors.outline.copy(alpha = if (enabled) .8f else .35f)),
        content = content)
}

@Composable internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (!liveBlurActive()) {
        ElevatedCard(modifier = modifier, content = content)
        return
    }
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Card(modifier = modifier.ultraGlass(shape, colors.surface.copy(alpha = .56f), strong = true)
        .border(1.dp, colors.outline.copy(alpha = .5f), shape), shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent), content = content)
}

