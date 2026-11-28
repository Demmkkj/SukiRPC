package dev.nuvio.presence

import dev.nuvio.presence.ui.themes.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage

@Composable private fun Poster(url:String,enabled:Boolean,width:Int){Box(Modifier.width(width.dp).height((width*1.4).dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){if(enabled&&url.isNotEmpty())AsyncImage(model=url,contentDescription="Pôster da obra",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())else Icon(Icons.Filled.PlayArrow,contentDescription=null,tint=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(32.dp))}}
@Composable internal fun Preview(p:Playback?,now:Long,o:Options){GlassCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(if(o.watching)"ASSISTINDO" else "JOGANDO",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);if(p==null){Text("Aguardando conteúdo");return@Column};Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Poster(p.artwork,o.artwork,66);Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text("Nuvio TV",fontWeight=FontWeight.SemiBold);Text(p.title);if(o.episode)Text(p.episode,style=MaterialTheme.typography.bodySmall);if(o.timestamps)Text((if(p.state=="paused")"Pausado · " else "")+timestamp(p.positionAt(now)),color=MaterialTheme.colorScheme.primary)}};Text("Prévia local • o Discord pode renderizar de outra forma",style=MaterialTheme.typography.labelSmall)}}}
