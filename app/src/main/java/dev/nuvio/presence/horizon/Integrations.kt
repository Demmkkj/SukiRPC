package dev.nuvio.presence

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.media.session.MediaSessionManager
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.nuvio.presence.ui.genesis.GenesisGap
import dev.nuvio.presence.ui.genesis.GenesisItemHeight
import dev.nuvio.presence.ui.genesis.genesisMotion
import dev.nuvio.presence.ui.renaissance.SectionLabel
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal class Integrations(c:Context){
    val prefs=Settings.prefs(c)
    fun enabled()=prefs.getStringSet("hz.enabled",emptySet()).orEmpty()
    fun blocked()=prefs.getStringSet("hz.blocked",emptySet()).orEmpty()
    fun accepts(pkg:String)=Horizon.allowed(pkg,enabled(),blocked())
    fun artwork(pkg:String)=prefs.getBoolean("hz.art."+pkg,true)
    fun clearOnPause(pkg:String)=prefs.getBoolean("hz.clearPaused."+pkg,pkg=="com.google.android.youtube")
    fun rank(pkg:String)=prefs.getInt("hz.rank."+pkg,0)
    fun ordered()=((enabled()+Horizon.nuvio)-blocked()).sortedWith(compareByDescending<String>{rank(it)}.thenBy{it}).take(32)
    fun toggle(pkg:String,on:Boolean){prefs.edit().putStringSet("hz.enabled",if(on)enabled()+pkg else enabled()-pkg)
        .putStringSet("hz.blocked",if(on)blocked()-pkg else blocked()+pkg).apply()}
}
internal fun sourceName(p:Playback?,side:String):String=Horizon.describe(SourceId(if(side=="mobile")Origin.Mobile else Origin.TV,
    p?.appId?.ifBlank{null}?:if(side=="mobile")"com.nuvio.app" else "com.nuvio.tv"))?.label?:"Mídia"


private data class AppPresentation(val name:String,val icon:ImageBitmap?=null)

private fun friendlyName(pkg:String)=when(pkg){
    "com.nuvio.app","com.nuviodebug.com" -> "Nuvio Mobile"
    "com.nuvio.tv" -> "Nuvio TV"
    else -> Horizon.catalog[pkg]?:pkg.substringAfterLast('.').replaceFirstChar{it.uppercase()}
}

@Composable private fun rememberAppPresentation(pkg:String,revision:Int):AppPresentation {
    val context=LocalContext.current.applicationContext
    val fallback=remember(pkg){AppPresentation(friendlyName(pkg))}
    val result by produceState(initialValue=fallback,context,pkg,revision){
        value=withContext(Dispatchers.IO){
            runCatching {
                val pm=context.packageManager
                val app=pm.getApplicationInfo(pkg,0)
                val name=if(pkg in Horizon.catalog || pkg in Horizon.nuvio) fallback.name
                    else pm.getApplicationLabel(app).toString().ifBlank{fallback.name}
                val drawable=pm.getApplicationIcon(app)
                val bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                if (drawable is AdaptiveIconDrawable) {
                    // Adaptive layers are 108 units for a 72-unit visible area.
                    listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
                        layer.setBounds(-24, -24, 120, 120)
                        layer.draw(canvas)
                    }
                } else {
                    val width = drawable.intrinsicWidth.coerceAtLeast(1)
                    val height = drawable.intrinsicHeight.coerceAtLeast(1)
                    val scale = 64f / maxOf(width, height)
                    val w = (width * scale).roundToInt().coerceAtLeast(1)
                    val h = (height * scale).roundToInt().coerceAtLeast(1)
                    val left = (96 - w) / 2
                    val top = (96 - h) / 2
                    drawable.setBounds(left, top, left + w, top + h)
                    drawable.draw(canvas)
                }
                AppPresentation(name,bitmap.asImageBitmap())
            }.getOrDefault(fallback)
        }
    }
    return result
}

// Local, lightweight pictograms for absent apps. Installed apps use their actual icons.
@Composable private fun IntegrationIcon(pkg:String,app:AppPresentation){
    val shape=CircleShape
    if(app.icon!=null){
        Box(Modifier.size(42.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){
            Image(app.icon,contentDescription=null,modifier=Modifier.fillMaxSize())
        }
        return
    }
    val name=app.name
    val accent=when {
        pkg.startsWith("com.google.android.youtube") || pkg=="com.google.android.apps.youtube.music" -> Color(0xffe62117)
        pkg=="com.stremio.one" -> Color(0xff7560d6)
        pkg.startsWith("org.jellyfin") -> Color(0xff7556ce)
        pkg=="com.plexapp.android" -> Color(0xff292c32)
        pkg=="org.xbmc.kodi" -> Color(0xff159bd2)
        pkg.startsWith("com.spotify") -> Color(0xff1db954)
        pkg=="org.videolan.vlc" -> Color(0xffe77c23)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val branded=Horizon.catalog.containsKey(pkg)
    Box(Modifier.size(42.dp).clip(shape).background(accent),contentAlignment=Alignment.Center){
        if(branded && (name.startsWith("YouTube")||name=="Stremio"||name.startsWith("Jellyfin")||name=="Plex"||name=="Kodi"||name.startsWith("Spotify")||name=="VLC")){
            Canvas(Modifier.size(28.dp)){
                val w=size.width;val h=size.height
                fun polygon(vararg xy:Float)=Path().apply{
                    moveTo(xy[0]*w,xy[1]*h)
                    for(i in 2 until xy.size step 2)lineTo(xy[i]*w,xy[i+1]*h)
                    close()
                }
                when {
                    name.startsWith("YouTube") -> drawPath(polygon(.30f,.15f,.85f,.5f,.30f,.85f),Color.White)
                    name=="Stremio" -> {
                        drawPath(polygon(.5f,.02f,.98f,.5f,.5f,.98f,.02f,.5f),Color.White.copy(alpha=.30f))
                        drawPath(polygon(.35f,.23f,.80f,.5f,.35f,.77f),Color.White)
                    }
                    name.startsWith("Jellyfin") -> {
                        drawPath(polygon(.5f,.06f,.96f,.90f,.04f,.90f),Color(0xff63d8ff))
                        drawPath(polygon(.5f,.40f,.72f,.78f,.28f,.78f),accent)
                    }
                    name=="Plex" -> drawPath(polygon(.2f,.08f,.53f,.08f,.86f,.5f,.53f,.92f,.2f,.92f,.53f,.5f),Color(0xffefbc48))
                    name=="Kodi" -> {
                        drawPath(polygon(.38f,.03f,.02f,.40f,.38f,.77f),Color.White)
                        drawPath(polygon(.49f,.03f,.95f,.36f,.70f,.58f,.49f,.41f),Color.White)
                        drawPath(polygon(.49f,.53f,.90f,.83f,.49f,.98f),Color.White.copy(alpha=.85f))
                    }
                    name.startsWith("Spotify") -> {
                        repeat(3){i->drawArc(Color(0xff102b1b),205f,130f,false,
                            topLeft=Offset(w*(.08f+i*.07f),h*(.24f+i*.17f)),
                            size=Size(w*(.84f-i*.14f),h*.36f),
                            style=androidx.compose.ui.graphics.drawscope.Stroke(width=w*.085f,cap=androidx.compose.ui.graphics.StrokeCap.Round))}
                    }
                    name=="VLC" -> {
                        drawPath(polygon(.5f,.06f,.86f,.88f,.14f,.88f),Color.White)
                        drawRoundRect(accent,topLeft=Offset(w*.28f,h*.52f),size=Size(w*.44f,h*.14f),cornerRadius=CornerRadius(w*.03f))
                    }
                }
            }
        }else Text(name.take(1).uppercase(),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,
            color=MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable private fun IntegrationDrawer(
    title:String,status:String? = null,expanded:Boolean,onToggle:()->Unit,
    icon:@Composable ()->Unit, content:@Composable ColumnScope.()->Unit
){
    val duration=genesisMotion(220)
    val angle by animateFloatAsState(if(expanded)180f else 0f,tween(durationMillis=duration),label="integrationChevron")
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .clickable(role=Role.Button,onClick=onToggle)
                    .semantics{stateDescription=if(expanded)"Expandido" else "Recolhido"}
                    .heightIn(min=GenesisItemHeight),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(14.dp)
            ){
                icon()
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    status?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Icon(Icons.Filled.KeyboardArrowDown,contentDescription=null,
                    modifier=Modifier.rotate(angle),tint=MaterialTheme.colorScheme.primary)
            }
            AnimatedVisibility(
                visible=expanded,
                enter=expandVertically(animationSpec=tween(durationMillis=duration),expandFrom=Alignment.Top)+fadeIn(tween(durationMillis=duration)),
                exit=shrinkVertically(animationSpec=tween(durationMillis=duration),shrinkTowards=Alignment.Top)+fadeOut(tween(durationMillis=duration))
            ){
                Column(Modifier.fillMaxWidth().padding(top=4.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    content()
                }
            }
        }
    }
}

private fun extensionCategory(pkg: String): String = when (pkg) {
    in Horizon.nuvio -> "Nuvio"
    "com.spotify.music", "com.spotify.tv.android", "com.google.android.apps.youtube.music" -> "Músicas"
    "com.google.android.youtube", "com.google.android.youtube.tv" -> "Vídeos"
    "com.stremio.one", "org.jellyfin.mobile", "org.jellyfin.androidtv", "com.plexapp.android", "org.xbmc.kodi" -> "Séries e filmes"
    "org.videolan.vlc", "is.xyz.mpv" -> "Reprodutores"
    else -> "Outros"
}

@Composable internal fun IntegrationHub(d:Dashboard, padding: PaddingValues){
    val c=LocalContext.current;val rules=remember(c){Integrations(c)}
    var rev by remember{mutableIntStateOf(0)}
    var expanded by rememberSaveable{mutableStateOf<String?>(null)}
    val permission=NuvioMobileProvider.allowed(c)
    val sessions=remember(rev,permission){
        if(permission)runCatching{c.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(NuvioMobileProvider.component(c)).map{it.packageName}}.getOrDefault(emptyList())
        else emptyList()
    }
    val packages=remember(rev,sessions){
        listOf("com.nuvio.app","com.nuvio.tv").plus(Horizon.catalog.keys).plus(sessions).plus(rules.enabled()).distinct()
    }
    val groups = remember(packages) { packages.groupBy(::extensionCategory) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(), contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(GenesisGap)
    ) {
        groups.forEach { (category, apps) ->
            item("category:$category") { SectionLabel(category) }
            items(apps, key = { "app:$it" }, contentType = { "extension" }) { pkg ->
                val app=rememberAppPresentation(pkg,rev)
                var on by remember(rev){mutableStateOf(rules.accepts(pkg))}
                var art by remember(rev){mutableStateOf(rules.artwork(pkg))}
                var clearPaused by remember(rev){mutableStateOf(rules.clearOnPause(pkg))}
                var rank by remember(rev){mutableIntStateOf(rules.rank(pkg).coerceIn(0,3))}
                val status = when {
                    !on -> "Desativada"
                    d.running && d.playback?.appId == pkg -> "Em uso"
                    pkg in sessions -> "Detectada"
                    else -> "Ativada"
                }
                IntegrationDrawer(app.name, status, expanded == pkg, { expanded = if (expanded == pkg) null else pkg },
                    icon={IntegrationIcon(pkg,app)}){
                    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.45f)){
                        Text(
                            when{
                                pkg in sessions -> "Sessão local detectada"
                                !permission -> "Acesso às notificações necessário para detectar sessões locais"
                                else -> "Sem reprodução local"
                            },
                            Modifier.fillMaxWidth().padding(10.dp),
                            style=MaterialTheme.typography.bodySmall
                        )
                    }
                    SettingToggle("Ativar integração",on){on=it;rules.toggle(pkg,it)}
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    SettingToggle("Mostrar capa",art){art=it;rules.prefs.edit().putBoolean("hz.art."+pkg,it).apply()}
                    if (d.playback?.appId == pkg && d.artworkStatus.isNotBlank()) {
                        Text(d.artworkStatus, style = MaterialTheme.typography.bodySmall)
                        if (d.artworkForwarded) Text("Imagem encaminhada ao SDK", style = MaterialTheme.typography.labelSmall)
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    SettingToggle("Limpar presença ao pausar",clearPaused){clearPaused=it;rules.prefs.edit().putBoolean("hz.clearPaused."+pkg,it).apply()}
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Text("Prioridade",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                        Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.primaryContainer){
                            Text(rank.toString(),Modifier.padding(horizontal=12.dp,vertical=6.dp),
                                color=MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Slider(value=rank.toFloat(),onValueChange={
                        val next=it.roundToInt().coerceIn(0,3)
                        if(rank!=next){rank=next;rules.prefs.edit().putInt("hz.rank."+pkg,next).apply()}
                    },valueRange=0f..3f,steps=2,modifier=Modifier.semantics{stateDescription="Prioridade $rank de 3"})
                    Text("Maior valor = maior prioridade.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item("general") {
            IntegrationDrawer("Configurações gerais", expanded = expanded == "__general",
                onToggle = { expanded = if (expanded == "__general") null else "__general" },
                icon={Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center){Text("⚙",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)}}){
                Text("Mobile e Auto usam sessões autorizadas. Na TV, aplicativos adicionais exigem Collector 2.0.",style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={c.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}){Text(if(permission)"Gerenciar acesso às notificações" else "Autorizar acesso às notificações")}
                TextButton(onClick={rev++}){Text("Atualizar sessões detectadas")}
                val privacy=readPrivacy(rules.prefs)
                val preview=mediaPresence(d.playback,Settings.options(c),readFormat(rules.prefs.all),privacy,d.connection.substringBefore(" •"),android.os.SystemClock.elapsedRealtime(),System.currentTimeMillis())
                Text("Prévia · "+(preview?.let{listOf(it.activityName,it.title,it.detail).filter(String::isNotBlank).joinToString(" · ")}?:"Sem presença de mídia"),style=MaterialTheme.typography.bodySmall)
                var preset by remember{mutableStateOf(rules.prefs.getString("hz.studio","").orEmpty())}
                val saved=remember(rev){RpcStore.load(c)}
                Text("Auto · preset quando a mídia estiver inativa")
                TextButton(onClick={preset="";rules.prefs.edit().remove("hz.studio").apply()}){Text(if(preset.isBlank())"✓ Desativado" else "Desativar")}
                saved.forEach{item->TextButton(onClick={preset=item.name;rules.prefs.edit().putString("hz.studio",preset).apply()}){Text((if(preset==item.name)"✓ " else "")+item.name)}}
            }
        }
    }
}
