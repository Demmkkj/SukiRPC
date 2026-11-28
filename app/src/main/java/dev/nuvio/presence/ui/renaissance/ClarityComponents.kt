package dev.nuvio.presence.ui.renaissance

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nuvio.presence.ui.themes.*
import dev.nuvio.presence.ui.genesis.genesisMotion

@Composable internal fun SoftAction(
    label:String, icon:ImageVector, action:()->Unit, modifier:Modifier=Modifier, enabled:Boolean=true
){
    val colors=MaterialTheme.colorScheme
    FilledTonalButton(onClick=action,enabled=enabled,modifier=modifier.heightIn(min=48.dp),
        shape=RoundedCornerShape(14.dp),
        colors=ButtonDefaults.filledTonalButtonColors(containerColor=colors.primary.copy(alpha=.13f),contentColor=colors.primary),
        contentPadding=PaddingValues(horizontal=10.dp,vertical=10.dp)){
        Icon(icon,contentDescription=null,modifier=Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label,maxLines=2,style=MaterialTheme.typography.labelMedium)
    }
}

@Composable internal fun ClarityDrawer(
    title:String,open:Boolean,onToggle:()->Unit,enabled:Boolean=true,
    content: @Composable ColumnScope.() -> Unit
){
    val duration=genesisMotion(220)
    val rotation by animateFloatAsState(if(open)180f else 0f,tween(durationMillis=duration),label="clarityChevron")
    ThemePanel(Modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .clickable(enabled=enabled,role=Role.Button,onClick=onToggle)
            .semantics{stateDescription=if(open)"Expandido" else "Recolhido"}.heightIn(min=48.dp),
            verticalAlignment=Alignment.CenterVertically){
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Icon(Icons.Filled.KeyboardArrowDown,null,Modifier.rotate(rotation),tint=MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(visible=open,
            enter=expandVertically(tween(durationMillis=duration),expandFrom=Alignment.Top)+fadeIn(tween(durationMillis=duration)),
            exit=shrinkVertically(tween(durationMillis=duration),shrinkTowards=Alignment.Top)+fadeOut(tween(durationMillis=duration))){
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)){
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }
}

@Composable internal fun SpatialDeviceCard(
    name:String,status:String,highlight:Boolean,detailsOpen:Boolean,onDetails:()->Unit,
    actions: @Composable RowScope.() -> Unit
){
    val colors=MaterialTheme.colorScheme
    val mode=LocalGraphics.current
    val shape = MaterialTheme.shapes.large
    val material = if (mode != GraphicsMode.ECO && highlight)
        Brush.linearGradient(listOf(colors.primary.copy(alpha = .15f), Color.Transparent)) else null
    val background = Modifier.fillMaxWidth().clip(shape).background(colors.surface)
    Column(
        (if (material != null) background.background(material) else background).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(MaterialTheme.shapes.small)
                .background(colors.primary.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(24.dp)) {
                    val stroke = 1.7.dp.toPx()
                    drawRoundRect(
                        colors.primary, topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height * .66f),
                        cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(stroke)
                    )
                    drawLine(colors.primary, Offset(size.width / 2, size.height * .69f),
                        Offset(size.width / 2, size.height * .90f), stroke)
                    drawLine(colors.primary, Offset(size.width * .30f, size.height * .90f),
                        Offset(size.width * .70f, size.height * .90f), stroke, StrokeCap.Round)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(status, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            actions()
            SoftAction(if(detailsOpen)"Fechar" else "Detalhes",Icons.Filled.KeyboardArrowDown,onDetails,Modifier.weight(1f))
        }
    }
}
