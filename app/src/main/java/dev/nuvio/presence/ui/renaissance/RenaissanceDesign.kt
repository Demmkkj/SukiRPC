package dev.nuvio.presence.ui.renaissance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nuvio.presence.R

internal val Void = Color(0xff0b0c0f)
internal val Graphite = Color(0xff171a20)
internal val Signal = Color(0xff8c9cff)
internal val Ivory = Color(0xffedf0f8)
internal val Muted = Color(0xffa3a8b6)
internal val Line = Color(0xff343944)
internal val Mono = FontFamily(Font(R.font.jetbrains_mono))
private val Manrope = FontFamily(Font(R.font.manrope))

internal fun renaissanceTypography():Typography{
    val t=Typography()
    return Typography(
        displayLarge=t.displayLarge.copy(fontFamily=Manrope,fontWeight=FontWeight.SemiBold),
        displayMedium=t.displayMedium.copy(fontFamily=Manrope,fontWeight=FontWeight.SemiBold),
        displaySmall=t.displaySmall.copy(fontFamily=Manrope,fontWeight=FontWeight.SemiBold),
        headlineLarge=t.headlineLarge.copy(fontFamily=Manrope,fontWeight=FontWeight.SemiBold,letterSpacing=(-.6).sp),
        headlineMedium=t.headlineMedium.copy(fontFamily=Manrope,fontWeight=FontWeight.SemiBold,letterSpacing=(-.4).sp),
        headlineSmall=t.headlineSmall.copy(fontFamily=Manrope,fontWeight=FontWeight.SemiBold),
        titleLarge=t.titleLarge.copy(fontFamily=Manrope,fontWeight=FontWeight.SemiBold),
        titleMedium=t.titleMedium.copy(fontFamily=Manrope,fontWeight=FontWeight.SemiBold),
        titleSmall=t.titleSmall.copy(fontFamily=Manrope,fontWeight=FontWeight.Medium),
        bodyLarge=t.bodyLarge.copy(fontFamily=Manrope),
        bodyMedium=t.bodyMedium.copy(fontFamily=Manrope),
        bodySmall=t.bodySmall.copy(fontFamily=Manrope),
        labelLarge=t.labelLarge.copy(fontFamily=Manrope,fontWeight=FontWeight.SemiBold),
        labelMedium=t.labelMedium.copy(fontFamily=Manrope),
        labelSmall=t.labelSmall.copy(fontFamily=Manrope)
    )
}

@Composable internal fun SignalLabel(label:String,active:Boolean){
    val color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label,style=MaterialTheme.typography.labelSmall,fontFamily=Mono,color=color,letterSpacing=.7.sp)
    }
}
@Composable internal fun SectionLabel(label:String){
    Text(label,style=MaterialTheme.typography.labelMedium,fontFamily=Mono,
        color=MaterialTheme.colorScheme.onSurfaceVariant,letterSpacing=.6.sp)
}
