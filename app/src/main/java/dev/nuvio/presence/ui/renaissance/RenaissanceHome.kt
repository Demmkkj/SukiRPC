package dev.nuvio.presence.ui.renaissance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.nuvio.presence.*
import dev.nuvio.presence.ui.themes.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable internal fun RenaissanceHome(
    dashboard:Dashboard,now:Long,options:Options,ready:Boolean,paired:Boolean,
    onDevices:()->Unit,onSources:()->Unit,onStudio:()->Unit,onHistory:()->Unit,
    onStop:()->Unit,onStart:(String)->Unit
){
    val ctx=LocalContext.current
    val prefs=remember(ctx){Settings.prefs(ctx)}
    var source by rememberSaveable{mutableStateOf(if(paired)"tv" else "auto")}
    val running=dashboard.running
    val stopping=dashboard.connection=="Encerrando"
    val privacy=readPrivacy(prefs)
    val format=readFormat(prefs.all)
    val p=dashboard.playback
    val studio=dashboard.studio?.takeIf{privacy.hidden.isEmpty()&&!privacy.blocks(it.name)}
    val preview=mediaPresence(p,options,format,privacy,dashboard.connection.substringBefore(" •"),now,System.currentTimeMillis())
    val media=if(running)preview else null
    val custom=if(running)studio else null
    SignalLabel(if(stopping)"ENCERRANDO" else if(running)"SESSÃO ATIVA" else if(ready)"PRONTO PARA CONECTAR" else "SDK INDISPONÍVEL",running&&!stopping)
    ThemePanel(Modifier.fillMaxWidth()){
        SectionLabel(if(custom!=null)"RPC PERSONALIZADO" else "ATIVIDADE ATUAL")
        if(media!=null||custom!=null){
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
                val artwork=media?.art.orEmpty()
                Box(Modifier.size(76.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primary.copy(alpha=.13f)),contentAlignment=Alignment.Center){
                    if(artwork.isNotBlank())AsyncImage(model=artwork,contentDescription="Capa da atividade",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    else Text("s.",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    Text(custom?.name?:media?.title?.ifBlank{"Atividade privada"}?:"Atividade",style=MaterialTheme.typography.titleLarge,maxLines=3,overflow=TextOverflow.Ellipsis)
                    val detail=custom?.details?:media?.detail.orEmpty()
                    if(detail.isNotBlank())Text(detail,style=MaterialTheme.typography.bodySmall,maxLines=3,overflow=TextOverflow.Ellipsis)
                }
            }
            if(media!=null&&p!=null&&options.timestamps&&Hidden.Progress !in privacy.hidden&&format.clock!=ClockDisplay.Off&&p.duration>0&&p.position>=0){
                LinearProgressIndicator(progress={(p.positionAt(now).toDouble()/p.duration).coerceIn(0.0,1.0).toFloat()},modifier=Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.small))
                Text("${timestamp(p.positionAt(now))} / ${timestamp(p.duration)}",fontFamily=Mono,style=MaterialTheme.typography.labelSmall)
            }
        }else{
            Text(if(running)"Aguardando atividade" else "Nenhuma presença ativa",style=MaterialTheme.typography.titleLarge)

        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("Discord",style=MaterialTheme.typography.titleMedium)
                Text(if(ready)dashboard.discord else "SDK indisponível",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(running)OutlinedButton(onClick=onStop,enabled=!stopping){Text(if(stopping)"Limpando" else "Parar")}
        }
    }
    if(!running){
        ThemePanel(Modifier.fillMaxWidth()){
            SectionLabel("FONTE DE ATIVIDADE")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("auto" to "Auto","mobile" to "Mobile","tv" to "TV").forEach{(key,label)->
                    FilterChip(selected=source==key,onClick={source=key},label={Text(label,maxLines=1)},modifier=Modifier.weight(1f))
                }
            }
            GlassButton(onClick={onStart(source)},enabled=ready,modifier=Modifier.fillMaxWidth()){
                Text(if(source=="tv"&&!paired)"Configurar TV" else "Iniciar sincronização")
            }
        }
    }
    Text("Acesso rápido",style=MaterialTheme.typography.titleLarge)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        QuickTile("Criar RPC",Icons.Filled.Add,onStudio,Modifier.weight(1f))
        QuickTile("Dispositivos",Icons.Filled.Settings,onDevices,Modifier.weight(1f))
        QuickTile("Integrações",Icons.Filled.List,onSources,Modifier.weight(1f))
    }
    WeeklyActivity(onHistory)
}

@Composable private fun QuickTile(label:String,icon:ImageVector,action:()->Unit,modifier:Modifier){
    ThemePanel(modifier.clickable(onClick=action)){
        Icon(icon,contentDescription=null,tint=MaterialTheme.colorScheme.primary)
        Text(label,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
}
@Composable private fun WeeklyActivity(onHistory:()->Unit){
    val c=LocalContext.current
    val enabled=Settings.prefs(c).getBoolean("chron.enabled",false)
    val today=LocalDate.now()
    // Snapshot on entry, not a new polling loop. Chronicle remains the live detailed view.
    val buckets by produceState<List<Long>?>(null,enabled,today){
        value=if(enabled)runCatching{
            val rows=History.get(c).rows()
            (6 downTo 0).map{offset->
                val day=today.minusDays(offset.toLong())
                rows.filter{Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).toLocalDate()==day}.sumOf{it.watched}
            }
        }.getOrNull() else null
    }
    ThemePanel(Modifier.fillMaxWidth()){
        SectionLabel("ÚLTIMOS 7 DIAS")
        val data=buckets
        if(!enabled)Text("Histórico desativado",style=MaterialTheme.typography.bodyMedium)
        else if(data==null)Text("Histórico indisponível ou carregando.",style=MaterialTheme.typography.bodyMedium)
        else{
            val total=data.sum()
            Text("${total/3600000}h ${total/60000%60}m",style=MaterialTheme.typography.headlineLarge,fontFamily=Mono)
            val color=MaterialTheme.colorScheme.primary
            val track=MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(62.dp)){
                val peak=data.maxOrNull()?.coerceAtLeast(1L)?:1L
                val slot=size.width/7f
                data.forEachIndexed{i,v->
                    val width=slot*.56f
                    val left=slot*i+(slot-width)/2
                    drawRoundRect(track,Offset(left,0f),Size(width,size.height),CornerRadius(4.dp.toPx()))
                    val h=size.height*(v.toDouble()/peak).coerceIn(0.0,1.0).toFloat()
                    if(h>0)drawRoundRect(color.copy(alpha=if(i==6)1f else .65f),Offset(left,size.height-h),Size(width,h),CornerRadius(4.dp.toPx()))
                }
            }
            Text("Tempo ativo das sessões iniciadas no período.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick=onHistory){Text("Histórico")}
    }
}
