package dev.nuvio.presence

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import dev.nuvio.presence.ui.genesis.GenesisGap
import dev.nuvio.presence.ui.renaissance.ClarityDrawer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import dev.nuvio.presence.ui.renaissance.EditorSection
import dev.nuvio.presence.ui.themes.*
import androidx.compose.foundation.background
import kotlinx.coroutines.*

@Composable
internal fun RpcStudio(dashboard:Dashboard,padding:PaddingValues,onPublish:(RpcPreset)->Unit,onStop:()->Unit){
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    var section by rememberSaveable{mutableStateOf(1)}
    // Preserve unfinished edits as raw fields without requiring the draft to be valid.
    var p by rememberSaveable(stateSaver=Saver<RpcPreset,String>(save={RpcStore.encode(it)},restore={RpcStore.decode(it,false)})) { mutableStateOf(RpcPreset()) }
    var saved by remember{mutableStateOf(RpcStore.load(ctx))}
    var community by remember{mutableStateOf(emptyList<RpcPreset>())}
    var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var editIndex by rememberSaveable{mutableIntStateOf(-1)}
    var confirm by rememberSaveable{mutableStateOf(false)}
    var starting by remember{mutableStateOf(false)}
    var galleryLarge by rememberSaveable{mutableStateOf<Uri?>(null)}
    var gallerySmall by rememberSaveable{mutableStateOf<Uri?>(null)}
    var pickingLarge by rememberSaveable{mutableStateOf(true)}
    fun resetGallery(){galleryLarge=null;gallerySmall=null}
    val galleryPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null){if(pickingLarge){galleryLarge=uri;p=p.copy(largeImage="")}else{gallerySmall=uri;p=p.copy(smallImage="")};message="Imagem selecionada para prévia local. Para publicar, informe uma URL pública ou chave de asset."}
    }

    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)scope.launch{
            try{p=withContext(Dispatchers.IO){ctx.contentResolver.openInputStream(uri)?.use{RpcStore.decode(RpcStore.read(it))}?:error("Arquivo indisponível")};resetGallery();editIndex=-1;section=3;message="Preset importado. Revise antes de publicar."}
            catch(e:CancellationException){throw e}catch(e:Exception){message="JSON inválido ou incompatível."}
        }
    }
    var exportSnapshot by rememberSaveable{mutableStateOf("")}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        if(uri!=null)scope.launch{
            try{withContext(Dispatchers.IO){ctx.contentResolver.openOutputStream(uri)?.use{it.write(exportSnapshot.toByteArray())}?:error("Arquivo indisponível")};message="Preset exportado."}
            catch(e:CancellationException){throw e}catch(e:Exception){message="Não foi possível exportar."}
        }
    }
    
    LaunchedEffect(Unit) { if (section !in 1..3) section = 1 }
    LaunchedEffect(starting) {
        if (starting) { delay(1800); starting = false }
    }
    val studioStates = rememberSaveableStateHolder()
    studioStates.SaveableStateProvider(section) {
        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.fillMaxSize(), contentPadding = padding, state = listState,
            verticalArrangement = Arrangement.spacedBy(GenesisGap)
        ) {
            item("tabs") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Meus presets", 2 to "Comunidade", 3 to "Editor").forEach { (index, label) ->
                        FilterChip(selected = section == index, onClick = { section = index }, label = { Text(label) })
                    }
                }
            }
            if (dashboard.running) item("active") {
                ThemePanel(Modifier.fillMaxWidth()) {
                    Text(dashboard.studio?.name ?: "Sincronização ativa", style = MaterialTheme.typography.titleMedium)
                    Text(dashboard.discord, style = MaterialTheme.typography.bodySmall)
                    GlassOutlinedButton(onClick = onStop, enabled = dashboard.connection != "Encerrando") {
                        Text(if (dashboard.connection == "Encerrando") "Limpando presença…" else "Parar presença")
                    }
                }
            }
            if (message.isNotBlank()) item("message") {
                Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
            when (section) {
                1 -> {
                    item("preset-tools") {
                        Column {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassButton(onClick = { resetGallery(); p = RpcPreset(); editIndex = -1; section = 3 }) { Text("Novo") }
                                GlassOutlinedButton(onClick = {
                                    importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))
                                }) { Text("Importar JSON") }
                            }
                            if (saved.isEmpty()) Text("Nenhum preset salvo.")
                        }
                    }
                    itemsIndexed(saved, key = { index, item -> "preset:$index:${item.name}" }) { index, preset ->
                        PresetRow(preset, onEdit = { resetGallery(); p = preset; editIndex = index; section = 3 }, onDelete = {
                            val next = saved.filterIndexed { item, _ -> item != index }
                            runCatching { RpcStore.save(ctx, next) }
                                .onSuccess { saved = next; editIndex = -1; message = "Preset removido." }
                                .onFailure { message = "Não foi possível salvar." }
                        })
                    }
                }
                2 -> {
                    item("community-tools") {
                        GlassOutlinedButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    community = RpcStore.community()
                                    message = "${community.size} presets encontrados."
                                } catch (e: CancellationException) { throw e }
                                catch (_: Exception) { message = "Catálogo indisponível." }
                                finally { busy = false }
                            }
                        }) { Text(if (busy) "Carregando…" else "Atualizar catálogo") }
                    }
                    itemsIndexed(community, key = { index, item -> "community:$index:${item.name}" }) { _, preset ->
                        PresetRow(preset, onEdit = { resetGallery(); p = preset.copy(appPackage = ""); editIndex = -1; section = 3 })
                    }
                }
                3 -> item("editor") {
                    Column(verticalArrangement = Arrangement.spacedBy(GenesisGap)) {
                        StudioPreview(p,galleryLarge,gallerySmall)
                        EditorSection("Identidade",initiallyOpen=true){
                        StudioField("Nome da atividade",p.name,128){p=p.copy(name=it)}
                        StudioField("Detalhes",p.details,128){p=p.copy(details=it)}
                        StudioField("Estado",p.state,128){p=p.copy(state=it)}
                        Text("Tipo de atividade",style=MaterialTheme.typography.titleMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            rpcTypes.forEach{(key,label)->FilterChip(selected=p.type==key,onClick={p=p.copy(type=key)},label={Text(label)})}
                        }
                        }
                        EditorSection("Imagens e animações"){
                        Text("GIF animado: cole uma URL direta pública na imagem principal ou secundária. A prévia aceita arquivos locais e CDNs conhecidos (Tenor, Giphy, Imgur). Outras URLs válidas são enviadas ao Discord somente ao publicar.",style=MaterialTheme.typography.bodySmall)
                        StudioField("Imagem principal · URL, YouTube, GIF ou asset",p.largeImage,300){galleryLarge=null;p=p.copy(largeImage=it)}
                        GalleryControls(galleryLarge,p.largeImage.isNotBlank(),onPick={pickingLarge=true;galleryPicker.launch("image/*")},onClear={galleryLarge=null;p=p.copy(largeImage="")})
                        StudioField("Texto da imagem principal",p.largeText,128){p=p.copy(largeText=it)}
                        StudioField("Imagem secundária · URL, YouTube, GIF ou asset",p.smallImage,300){gallerySmall=null;p=p.copy(smallImage=it)}
                        GalleryControls(gallerySmall,p.smallImage.isNotBlank(),onPick={pickingLarge=false;galleryPicker.launch("image/*")},onClear={gallerySmall=null;p=p.copy(smallImage="")})
                        StudioField("Texto da imagem secundária",p.smallText,128){p=p.copy(smallText=it)}
                        }
                        EditorSection("Cronômetro"){
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            listOf("none" to "Oculto","elapsed" to "Decorrido","countdown" to "Regressivo").forEach{(key,label)->FilterChip(selected=p.timer==key,onClick={p=p.copy(timer=key)},label={Text(label)})}
                        }
                        if(p.timer=="countdown"){
                            var duration by remember(p.name){mutableStateOf(p.minutes.toString())}
                            StudioField("Duração em minutos (1–1440)",duration,4){duration=it.filter(Char::isDigit);p=p.copy(minutes=duration.toIntOrNull()?:0)}
                        }
                        }
                        EditorSection("Botões e links"){
                        StudioField("Botão 1 • texto",p.button1,32){p=p.copy(button1=it)}
                        StudioField("Botão 1 • URL HTTPS",p.url1,256){p=p.copy(url1=it)}
                        StudioField("Botão 2 • texto",p.button2,32){p=p.copy(button2=it)}
                        StudioField("Botão 2 • URL HTTPS",p.url2,256){p=p.copy(url2=it)}
                        }
                        EditorSection("Detecção automática"){
                        StudioField("Pacote Android (ex.: com.mojang.minecraftpe)",p.appPackage,200){p=p.copy(appPackage=it.trim())}
                        Text("Com um pacote definido, o RPC só aparece quando esse aplicativo está em primeiro plano. A detecção é local e experimental.",style=MaterialTheme.typography.bodySmall)
                        if(p.appPackage.isNotEmpty())GlassOutlinedButton(onClick={ctx.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))}){Text("Autorizar acesso ao uso")}
                        }
                        val error=if((galleryLarge!=null&&p.largeImage.isBlank())||(gallerySmall!=null&&p.smallImage.isBlank())) "Imagem local pendente: substitua por URL/asset ou remova antes de salvar/publicar." else p.error()
                        if(error!=null)Text(error,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                        GlassButton(enabled=error==null&&!dashboard.running&&!starting&&NativeRpc.ready,onClick={
                            if(p.appPackage.isNotEmpty()&&!RpcAppDetection.allowed(ctx))message="Autorize acesso ao uso antes de ativar." else confirm=true
                        },modifier=Modifier.fillMaxWidth()){Text("Publicar no Discord")}
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            GlassOutlinedButton(enabled=error==null,onClick={
                                val next=saved.toMutableList();if(editIndex in next.indices)next[editIndex]=p else if(p !in next)next.add(p)
                                runCatching{RpcStore.save(ctx,next)}.onSuccess{saved=next;editIndex=next.indexOf(p);message="Preset salvo."}.onFailure{message="Limite de armazenamento atingido."}
                            }){Text("Salvar")}
                            GlassOutlinedButton(enabled=error==null,onClick={exportSnapshot=RpcStore.encode(p);exporter.launch("rpc-preset.json")}){Text("Exportar JSON")}
                        }
                    }
                }
            }
            if (!dashboard.running) item("cleanup") {
                var open by rememberSaveable { mutableStateOf(false) }
                ClarityDrawer("Diagnóstico", open, { open = !open }) {
                    Text(dashboard.discord, style = MaterialTheme.typography.bodySmall)
                    GlassOutlinedButton(onClick = onStop, enabled = NativeRpc.ready) { Text("Forçar limpeza") }
                }
            }
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Publicar presença?")},text={Text("O nome, textos, imagens e links escolhidos serão enviados ao Discord. Outros usuários poderão vê-los. Esta é uma atividade personalizada, não uma conexão oficial com o jogo.")},confirmButton={TextButton(onClick={confirm=false;starting=true;onPublish(p)}){Text("Publicar")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancelar")}})
}
@Composable private fun GalleryControls(uri:Uri?,hosted:Boolean,onPick:()->Unit,onClear:()->Unit){
    GlassOutlinedButton(onClick=onPick,modifier=Modifier.fillMaxWidth()){Text(if(uri==null)"Escolher imagem/GIF da galeria" else "Trocar imagem/GIF da galeria")}
    if(uri!=null){
        Text(if(hosted)"Imagem com URL • prévia local" else "Prévia local. Para usar no Discord, informe uma URL HTTPS pública ou chave de asset.",style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            TextButton(onClick=onClear){Text("Remover")}
        }
    }
}
@Composable private fun StudioField(label:String,value:String,max:Int,onChange:(String)->Unit){
    OutlinedTextField(value,{onChange(it.take(max))},label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth())
}
@Composable private fun PresetRow(p:RpcPreset,onEdit:()->Unit,onDelete:(()->Unit)?=null){
    GlassCard(Modifier.fillMaxWidth()){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        StudioArtwork(p.largeImage,p.name,48)
        Column(Modifier.weight(1f)){Text(p.name,style=MaterialTheme.typography.titleMedium);Text(p.details,style=MaterialTheme.typography.bodySmall)}
        TextButton(onClick=onEdit){Text("Editar")};if(onDelete!=null)TextButton(onClick=onDelete){Text("Excluir")}
    }}
}
@Composable private fun StudioArtwork(url:String,name:String,size:Int,local:Uri?=null,wide:Boolean=false,animatedLoader:ImageLoader?=null){
    // Animate only the editor preview, not the catalogue or saved preset list.
    Box((if(wide)Modifier.fillMaxWidth().height(size.dp) else Modifier.size(size.dp)).clip(MaterialTheme.shapes.medium)){
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.secondaryContainer){Box(contentAlignment=Alignment.Center){Text(name.take(1).uppercase().ifBlank{"✦"},style=MaterialTheme.typography.titleLarge)}}
        val preview = studioPreviewUrl(url, animatedLoader != null)
        if (local != null && animatedLoader != null) {
            AsyncImage(model = local, imageLoader = animatedLoader, contentDescription = name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (cachedPublicArtwork(preview).isNotEmpty()) {
            AsyncImage(model = preview, imageLoader = ArtworkImages.loader(LocalContext.current),
                contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (preview.isNotEmpty()) {
            if (animatedLoader != null)
                AsyncImage(model = preview, imageLoader = animatedLoader, contentDescription = name,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else
                AsyncImage(model = preview, contentDescription = name, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
        }
    }
}
@Composable internal fun StudioPreview(p:RpcPreset,large:Uri?,small:Uri?){
    val context=LocalContext.current.applicationContext
    val loader=remember(context){
        ImageLoader.Builder(context).components {
            if(android.os.Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
            else add(GifDecoder.Factory())
        }.build()
    }
    DisposableEffect(loader){ onDispose { loader.shutdown() } }
    GlassCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Prévia no Discord · ${rpcTypes[p.type].orEmpty()}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Box{StudioArtwork(p.largeImage,p.name,84,large,animatedLoader=loader);if(p.smallImage.isNotBlank()||small!=null)Box(Modifier.align(Alignment.BottomEnd)){StudioArtwork(p.smallImage,p.smallText,28,small,animatedLoader=loader)}}
            Column(Modifier.weight(1f)){Text(p.name.ifBlank{"Nome da atividade"},style=MaterialTheme.typography.titleMedium);if(p.details.isNotBlank())Text(p.details);if(p.state.isNotBlank())Text(p.state);if(p.timer!="none")Text(if(p.timer=="elapsed")"00:00 decorridos" else "${p.minutes} min restantes",style=MaterialTheme.typography.labelMedium)}
        }
        listOf(p.button1,p.button2).filter{it.isNotBlank()}.forEach{label->Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()){Box(Modifier.padding(8.dp),contentAlignment=Alignment.Center){Text(label)}}}
    }}
}
