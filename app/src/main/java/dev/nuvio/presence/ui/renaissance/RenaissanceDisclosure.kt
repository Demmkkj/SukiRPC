package dev.nuvio.presence.ui.renaissance

import android.animation.ValueAnimator
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.nuvio.presence.ui.themes.*

@Composable internal fun EditorSection(title:String,initiallyOpen:Boolean=false,content:@Composable ColumnScope.()->Unit){
    if(LocalThemeId.current!=ThemeId.RENAISSANCE){
        Text(title,style=MaterialTheme.typography.titleMedium)
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
        return
    }
    var open by rememberSaveable(title){mutableStateOf(initiallyOpen)}
    val duration=if(ValueAnimator.areAnimatorsEnabled())240 else 0
    val rotation by animateFloatAsState(if(open)180f else 0f,tween(durationMillis=duration),label="editorChevron")
    ThemePanel(Modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(role=Role.Button){open=!open}
            .semantics{stateDescription=if(open)"Expandido" else "Recolhido"}.heightIn(min=48.dp),
            verticalAlignment=Alignment.CenterVertically){
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Icon(Icons.Filled.KeyboardArrowDown,null,modifier=Modifier.rotate(rotation),tint=MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(open,
            enter=expandVertically(tween(durationMillis=duration),expandFrom=Alignment.Top)+fadeIn(tween(durationMillis=duration)),
            exit=shrinkVertically(tween(durationMillis=duration),shrinkTowards=Alignment.Top)+fadeOut(tween(durationMillis=duration))){
            Column(Modifier.fillMaxWidth().padding(top=4.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                content()
            }
        }
    }
}
