package dev.nuvio.presence.ui.themes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.nuvio.presence.*

@Composable internal fun ThemedHome(playback:Playback?,now:Long,options:Options,running:Boolean,connection:String,discord:String,ready:Boolean,paired:Boolean,onDevices:()->Unit,onStop:()->Unit,onStart:(String)->Unit,onStudio:()->Unit){
    val id=LocalThemeId.current
    var source by rememberSaveable{mutableStateOf(if(paired)"tv" else "mobile")}
    val p=playback?.takeIf{it.valid}
    val hero:@Composable ()->Unit={
        ThemePanel(Modifier.fillMaxWidth()){
            if(id==ThemeId.OBSIDIAN){Text("01 / SESSION",style=MaterialTheme.typography.labelMedium);Text(if(running)"ONLINE" else "STANDBY",style=MaterialTheme.typography.headlineLarge)}
            else if(id==ThemeId.RELIC){Text("✦  O SANTUÁRIO  ✦",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,style=MaterialTheme.typography.titleLarge);HorizontalDivider()}
            else if(id==ThemeId.AERO){Row(verticalAlignment=Alignment.CenterVertically){Image(painterResource(dev.nuvio.presence.R.drawable.suki_icon_foreground),null,Modifier.size(54.dp));Column{Text("Bem-vindo!",style=MaterialTheme.typography.headlineMedium);Text("Seu mundo conectado")}}}
            else {Image(painterResource(dev.nuvio.presence.R.drawable.suki_icon_foreground),null,Modifier.align(Alignment.CenterHorizontally).size(if(id==ThemeId.LIQUID)56.dp else 82.dp));Text(if(running)"Sincronização ativa" else "Sua presença, do seu jeito.",Modifier.fillMaxWidth(),textAlign=TextAlign.Center,style=MaterialTheme.typography.headlineMedium)}
            Text(if(running)connection else if(ready)"Pronto para sincronizar" else "SDK indisponível",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val media:@Composable ()->Unit={ThemePanel(Modifier.fillMaxWidth()){
        Text(if(id==ThemeId.RELIC)"Registro de atividade" else "Reprodução",style=MaterialTheme.typography.titleLarge)
        if(p==null){Text("Nenhuma reprodução");Text("Abra o Nuvio para começar.",style=MaterialTheme.typography.bodySmall)}else{
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                if(options.artwork&&p.artwork.isNotBlank())AsyncImage(model=p.artwork,contentDescription="Capa",modifier=Modifier.size(64.dp,88.dp).clip(MaterialTheme.shapes.small),contentScale=ContentScale.Crop)
                Column(Modifier.weight(1f)){Text(p.title,style=MaterialTheme.typography.titleMedium,maxLines=3,overflow=TextOverflow.Ellipsis);if(options.episode)Text(p.episode,maxLines=2,overflow=TextOverflow.Ellipsis);Text(p.state,style=MaterialTheme.typography.labelMedium)}
            }
            if(options.timestamps&&p.duration>0&&p.position>=0){LinearProgressIndicator(progress={(p.positionAt(now).toDouble()/p.duration).coerceIn(0.0,1.0).toFloat()},modifier=Modifier.fillMaxWidth());Text("${timestamp(p.positionAt(now))} / ${timestamp(p.duration)}",style=MaterialTheme.typography.labelMedium)}
            val preview=presenceFor(p,options,now,System.currentTimeMillis())
            if(preview!=null&&running){HorizontalDivider();Text("Prévia Discord",style=MaterialTheme.typography.labelMedium);Text(preview.title);Text(preview.detail,style=MaterialTheme.typography.bodySmall)}
        }
    }}
    val controls:@Composable ()->Unit={ThemePanel(Modifier.fillMaxWidth()){
        Text(if(id==ThemeId.RELIC)"Escolher vínculo" else "Fonte de reprodução",style=MaterialTheme.typography.titleMedium)
        val sources=listOf("mobile" to "Mobile","tv" to "TV","auto" to "Auto")
        if(id==ThemeId.OBSIDIAN||id==ThemeId.RELIC){sources.forEach{(key,label)->Row(Modifier.fillMaxWidth().clickable(enabled=!running){source=key},verticalAlignment=Alignment.CenterVertically){RadioButton(selected=source==key,onClick={source=key},enabled=!running);Text(label)}}}
        else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){sources.forEach{(key,label)->FilterChip(selected=source==key,onClick={source=key},enabled=!running,label={Text(label,maxLines=1)},modifier=Modifier.weight(1f))}}
        if(!running&&source=="tv"&&!paired)Text("Pareie a TV antes de sincronizar.",style=MaterialTheme.typography.bodySmall)
        GlassButton(onClick={if(running)onStop()else onStart(source)},enabled=running||ready,modifier=Modifier.fillMaxWidth()){Text(if(running)"Encerrar sincronização" else if(source=="tv"&&!paired)"Configurar TV" else "Iniciar sincronização")}
        TextButton(onClick=onDevices){Text("Gerenciar dispositivos")}
    }}
    val discordPanel:@Composable ()->Unit={ThemePanel(Modifier.fillMaxWidth()){Text("Discord",style=MaterialTheme.typography.titleMedium);Text(if(ready)discord else "SDK indisponível");GlassOutlinedButton(onClick=onStudio,modifier=Modifier.fillMaxWidth()){Text("Abrir RPC Studio")}}}
    when(id){
        ThemeId.GENESIS,ThemeId.GENESIS_LIGHT,ThemeId.RENAISSANCE,ThemeId.HORIZON,ThemeId.CHRONICLE,ThemeId.ECHO,ThemeId.AURORA,ThemeId.NEBULA,ThemeId.PRISM,ThemeId.CRIMSON,ThemeId.FROST,ThemeId.ECLIPSE->{hero();media();controls();discordPanel()}
        ThemeId.OBSIDIAN->{hero();controls();discordPanel();media()}
        ThemeId.RELIC->{hero();controls();media();discordPanel()}
        ThemeId.PHANTOM->{hero();discordPanel();media();controls()}
        ThemeId.LIQUID->{hero();Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){ThemePanel(Modifier.fillMaxWidth()){Text("SDK",style=MaterialTheme.typography.labelMedium);Text(if(ready)"Pronto" else "Offline")}};Column(Modifier.weight(1f)){ThemePanel(Modifier.fillMaxWidth()){Text("Sessão",style=MaterialTheme.typography.labelMedium);Text(if(running)"Ativa" else "Pausada")}}};controls();media();discordPanel()}
        ThemeId.AERO->{hero();Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){GlassButton(onClick=onDevices,modifier=Modifier.weight(1f)){Text("Dispositivos")};GlassButton(onClick=onStudio,modifier=Modifier.weight(1f)){Text("RPC Studio")}};media();discordPanel();controls()}
    }
}


