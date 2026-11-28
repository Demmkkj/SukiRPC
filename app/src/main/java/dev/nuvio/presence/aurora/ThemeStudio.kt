package dev.nuvio.presence.ui.themes

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.nuvio.presence.*
import dev.nuvio.presence.ui.genesis.GenesisGap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.*
import java.io.File

internal class ThemeDraftState {
    var draft by mutableStateOf<ThemeBundle?>(null)
    var original by mutableStateOf<SavedSkin?>(null)
    var undo by mutableStateOf<ThemeBundle?>(null)
}

@Composable internal fun ThemeStudio(items:List<SavedSkin>, selected:String?, current:ThemeId,mode:GraphicsMode,
    padding: PaddingValues, editor: ThemeDraftState,
    apply:(ThemeId,String?)->Unit, refresh:()->Unit,onMode:(GraphicsMode)->Unit){
    val ctx=LocalContext.current
    val store=remember{ThemeStore(ctx)}
    val prefs=remember{Settings.prefs(ctx)}
    val scope=rememberCoroutineScope()
    var query by rememberSaveable{mutableStateOf("")}
    var filter by rememberSaveable{mutableStateOf("Todos")}
    var byDate by rememberSaveable{mutableStateOf(false)}
    var favorites by remember{mutableStateOf(prefs.getStringSet("aurora.favorites",emptySet()).orEmpty().toSet())}
    var preview by remember { mutableStateOf<ThemeBundle?>(null) }
    var msg by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var output by remember{mutableStateOf<ThemeBundle?>(null)}
    var deletion by remember{mutableStateOf<SavedSkin?>(null)}
    fun change(b:ThemeBundle){editor.undo=editor.draft;editor.draft=b}
    fun job(action:suspend ()->Unit){if(busy)return;busy=true;scope.launch{try{action()}catch(e:CancellationException){throw e}catch(e:Exception){msg="Operação recusada. Confira o formato, os limites e o destino."}finally{busy=false}}}
    fun fav(keys:Set<String>){favorites=keys;prefs.edit().putStringSet("aurora.favorites",keys).apply()}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)job{
        withContext(Dispatchers.IO){val bytes=requireNotNull(ctx.contentResolver.openInputStream(uri)).use{bounded(it,FILE_LIMIT)};store.import(bytes)}
        refresh();msg="Tema importado"
    }}
    val photo=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)job{
        val image=withContext(Dispatchers.IO){decodePhoto(requireNotNull(ctx.contentResolver.openInputStream(uri)).use{bounded(it,IMAGE_LIMIT)})}
        editor.draft?.let{change(it.copy(image=image))}
    }}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        val capture=output;output=null
        if(uri!=null && capture!=null)job{withContext(Dispatchers.IO){requireNotNull(ctx.contentResolver.openOutputStream(uri,"wt")).use{it.write(ThemeFile.encode(capture))}};msg="Tema exportado"}
    }
    if (busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(padding))
        return
    }
    val edit = editor.draft
    if (edit != null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(GenesisGap)) {
        if (msg.isNotEmpty()) Text(msg)
        val s = edit.skin
        fun update(next:Skin) = change(edit.copy(skin = next))
        Text("Editor de tema", style = MaterialTheme.typography.headlineMedium)
        ThemeSample(edit)
        OutlinedTextField(s.name,{update(s.copy(name=it.take(64)))},label={Text("Nome")})
        OutlinedTextField(s.author,{update(s.copy(author=it.take(64)))},label={Text("Autor")})
        OutlinedTextField(s.description,{update(s.copy(description=it.take(256)))},label={Text("Descrição")})
        OutlinedTextField(s.version,{update(s.copy(version=it.take(32)))},label={Text("Versão do tema")})
        colorKeys.forEach{key->
            var entry by remember(key,s.colors[key]){mutableStateOf(s.colors.getValue(key))}
            OutlinedTextField(entry,{entry=it;if(it.matches(Regex("#[0-9a-fA-F]{6}")))update(s.copy(colors=s.colors+(key to it)))},
                label={Text("$key · #RRGGBB")},singleLine=true)
        }
        ranges.forEach{(key,range)->Text("$key · ${s.numbers.getValue(key)}")
            Slider(s.numbers.getValue(key).toFloat(),{update(s.copy(numbers=s.numbers+(key to it.toInt())))},valueRange=range.first.toFloat()..range.last.toFloat())}
        choices.forEach{(key,values)->TextButton(onClick={val index=values.indexOf(s.styles[key]);update(s.copy(styles=s.styles+(key to values[(index+1)%values.size])))}){Text("$key · ${s.styles[key]}")}}
        OutlinedButton(onClick={photo.launch(arrayOf("image/*"))}){Text("Escolher fundo")}
        if(edit.image!=null)TextButton(onClick={change(edit.copy(image=null))}){Text("Remover fundo")}
        TextButton(onClick={editor.undo?.let{editor.draft=it;editor.undo=null}},enabled=editor.undo!=null){Text("Desfazer")}
        TextButton(onClick={change(ThemeBundle(officialSkin(ThemeId.valueOf(s.base)).copy(name=s.name)))}){Text("Restaurar padrão")}
        Button(enabled=s.name.isNotBlank(),onClick={job{
            val old=editor.original
            val saved=withContext(Dispatchers.IO){store.save(edit,old?.imported==true)}
            if(selected==old?.id && old!=null)apply(ThemeId.valueOf(s.base),saved.id)
            if(old!=null){if(old.id in favorites)fav(favorites-old.id+saved.id);withContext(Dispatchers.IO){store.remove(old)}}
            editor.draft=null;editor.original=null;editor.undo=null;refresh()
        }}){Text("Salvar")}
        TextButton(onClick={editor.draft=null;editor.original=null;editor.undo=null}){Text("Cancelar")}
        }
        return
    }
    val genesis = setOf(ThemeId.GENESIS.name, ThemeId.GENESIS_LIGHT.name)
    val builtins = remember { ThemeId.entries.map { Triple(it.name, officialSkin(it), null as SavedSkin?) } }
    val rows = remember(items) { builtins + items.map { Triple(it.id, it.skin, it) } }
    val visible = remember(rows, query, filter, favorites, byDate) {
        val sorted = if (byDate) rows.sortedByDescending { it.third?.date ?: 0 }
            else rows.sortedBy { it.second.name.lowercase() }
        sorted.sortedBy { (id) -> when (id) {
            ThemeId.GENESIS.name -> 0
            ThemeId.GENESIS_LIGHT.name -> 1
            else -> 2
        } }.filter { (id, skin, item) ->
            skin.name.contains(query, true) && when (filter) {
                "Genesis" -> id in genesis
                "Oficiais" -> item == null
                "Personalizados" -> item?.imported == false
                "Importados" -> item?.imported == true
                "Favoritos" -> id in favorites
                else -> true
            }
        }
    }
    LazyVerticalGrid(
        GridCells.Adaptive(148.dp), Modifier.fillMaxSize(), contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(GenesisGap),
        horizontalArrangement = Arrangement.spacedBy(GenesisGap)
    ) {
        item("quality", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Qualidade gráfica", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GraphicsMode.entries.forEach { quality ->
                        FilterChip(selected = mode == quality, onClick = { onMode(quality) },
                            label = { Text(quality.name.lowercase().replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
        }
        item("filters", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (msg.isNotEmpty()) Text(msg)
                OutlinedTextField(query, { query = it }, label = { Text("Buscar temas") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todos", "Genesis", "Oficiais", "Personalizados", "Importados", "Favoritos").forEach { group ->
                        FilterChip(selected = filter == group, onClick = { filter = group }, label = { Text(group) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Button(onClick = {
                        editor.original = null
                        editor.undo = null
                        editor.draft = ThemeBundle(officialSkin(current).copy(name = "Meu tema"))
                    }) { Text("Criar") }
                    TextButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Importar") }
                    TextButton(onClick = { byDate = !byDate }) { Text(if (byDate) "Por data" else "Por nome") }
                }
            }
        }
        items(visible, key = { it.first }, contentType = { "theme" }) { (id, skin, item) ->
            val active = selected == id || (selected == null && current.name == id)
            var menu by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val back = Color(android.graphics.Color.parseColor(skin.colors.getValue("background")))
                val accent = Color(android.graphics.Color.parseColor(skin.colors.getValue("primary")))
                val shape = MaterialTheme.shapes.medium
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(shape).background(back)
                    .clickable(onClickLabel = "Abrir prévia de ${skin.name}") {
                        job { preview = withContext(Dispatchers.IO) { item?.let(store::bundle) ?: ThemeBundle(skin) } }
                    }.padding(12.dp)) {
                    Box(Modifier.fillMaxWidth(.58f).fillMaxHeight(.65f).clip(shape).background(accent.copy(alpha = .26f)))
                    Box(Modifier.align(androidx.compose.ui.Alignment.BottomEnd).fillMaxWidth(.56f)
                        .height(14.dp).clip(shape).background(accent))
                }
                Text(skin.name, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Row {
                    TextButton(onClick = { apply(ThemeId.valueOf(skin.base), item?.id) }, enabled = !active) {
                        Text(if (active) "Em uso" else "Aplicar")
                    }
                    Box {
                        TextButton(onClick = { menu = true }) { Text("Mais") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(if (id in favorites) "Remover favorito" else "Favoritar") },
                                onClick = { menu = false; fav(if (id in favorites) favorites - id else favorites + id) })
                            DropdownMenuItem(text = { Text("Duplicar") }, onClick = {
                                menu = false
                                job {
                                    val copy = withContext(Dispatchers.IO) { item?.let(store::bundle) ?: ThemeBundle(skin) }
                                    editor.original = null
                                    editor.undo = null
                                    editor.draft = copy.copy(skin = skin.copy(name = skin.name.take(54) + " · cópia"))
                                }
                            })
                            if (item != null) {
                                DropdownMenuItem(text = { Text("Editar") }, onClick = {
                                    menu = false
                                    job {
                                        editor.draft = withContext(Dispatchers.IO) { store.bundle(item) }
                                        editor.original = item
                                        editor.undo = null
                                    }
                                })
                                DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; deletion = item })
                            }
                            DropdownMenuItem(text = { Text("Exportar") }, onClick = {
                                menu = false
                                job {
                                    output = withContext(Dispatchers.IO) { item?.let(store::bundle) ?: ThemeBundle(skin) }
                                    exporter.launch("SukiRPC.sukitheme")
                                }
                            })
                            DropdownMenuItem(text = { Text("Compartilhar") }, onClick = {
                                menu = false
                                job {
                                    val file = withContext(Dispatchers.IO) {
                                        val dir = File(ctx.cacheDir, "themes").apply { mkdirs() }
                                        File(dir, "$id.sukitheme").also {
                                            it.writeBytes(ThemeFile.encode(item?.let(store::bundle) ?: ThemeBundle(skin)))
                                        }
                                    }
                                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".updates", file)
                                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND)
                                        .setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
                                }
                            })
                        }
                    }
                }
            }
        }
    }
    preview?.let { bundle ->
        AlertDialog(onDismissRequest = { preview = null }, title = { Text(bundle.skin.name) },
            text = { ThemeSample(bundle) },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("Fechar") } })
    }
    deletion?.let{item->AlertDialog(onDismissRequest={deletion=null},title={Text("Excluir ${item.skin.name}?")},confirmButton={TextButton(onClick={deletion=null;job{if(selected==item.id)apply(current,null);withContext(Dispatchers.IO){store.remove(item)};fav(favorites-item.id);refresh()}}){Text("Excluir")}},dismissButton={TextButton(onClick={deletion=null}){Text("Cancelar")}})}
}

@Composable
private fun ThemeSample(bundle: ThemeBundle) {
    val skin = bundle.skin
    var checked by rememberSaveable { mutableStateOf(true) }
    CompositionLocalProvider(LocalSkin provides skin) {
        MetamorphosisTheme(ThemeId.valueOf(skin.base), GraphicsMode.BALANCED) {
            Box(Modifier.fillMaxWidth().height(196.dp).clip(MaterialTheme.shapes.large)) {
                ThemeBackdrop()
                ThemeWallpaper(bundle.image, skin, GraphicsMode.BALANCED)
                Box(Modifier.fillMaxWidth().padding(12.dp)) {
                    ThemePanel(Modifier.fillMaxWidth()) {
                        Text(skin.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        SettingToggle("Sincronização", checked) { checked = it }
                    }
                }
            }
        }
    }
}
