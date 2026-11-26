package dev.nuvio.presence

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment

@Composable internal fun SettingToggle(label:String,checked:Boolean,change:(Boolean)->Unit){Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(label,Modifier.weight(1f).padding(end=12.dp));Switch(checked=checked,onCheckedChange=change)}}
