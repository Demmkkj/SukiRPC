package dev.nuvio.presence

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nuvio.presence.ui.genesis.GenesisArtwork
import dev.nuvio.presence.ui.genesis.GenesisGap
import dev.nuvio.presence.ui.renaissance.ClarityDrawer
import dev.nuvio.presence.ui.renaissance.Mono
import dev.nuvio.presence.ui.renaissance.SectionLabel
import kotlinx.coroutines.*
import java.time.*
import java.time.temporal.TemporalAdjusters
import org.json.JSONObject

private fun day(ms:Long)=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
private fun duration(ms:Long)="${ms/3600000}h ${ms/60000%60}m ${ms/1000%60}s"
private fun stamp(ms:Long)=java.text.DateFormat.getDateTimeInstance().format(java.util.Date(ms))
@Composable private fun picker(label:String,options:List<String>,value:String,set:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    Box{TextButton(onClick={open=true}){Text("$label · $value")};DropdownMenu(open,{open=false}){options.forEach{v->DropdownMenuItem(text={Text(v)},onClick={set(v);open=false})}}}
}
private val chronicleTabs = listOf(
    "Todas", "Músicas", "Vídeos", "Séries e filmes", "Jogos", "Custom RPC", "Outros"
)

@Composable
private fun ChronicleDisclosure(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    ClarityDrawer(title, open, { open = !open }, content = content)
}

@Composable
private fun ChronicleTile(row: HistoryRow, select: () -> Unit) {
    val media = row.media
    val ratio = when (chronicleCategory(media)) {
        "Vídeos" -> 16f / 9f
        "Séries e filmes", "Jogos" -> 2f / 3f
        else -> 1f
    }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = select).padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        GenesisArtwork(media.cover, Modifier.fillMaxWidth().aspectRatio(ratio), cacheOnly = true,
            horizon = media.source != "RPC Studio" && media.category != "Custom RPC")
        Text(media.name.ifBlank { "Sem título" }, style = MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (media.episode.isNotBlank()) Text(media.episode, style = MaterialTheme.typography.bodySmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(duration(row.watched), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChronicleDetails(row: HistoryRow, close: () -> Unit, remove: () -> Unit) {
    val media = row.media
    AlertDialog(
        onDismissRequest = close,
        title = { Text(media.name.ifBlank { "Sem título" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (media.episode.isNotBlank()) Text(media.episode)
                if (media.source.isNotBlank()) Text(media.source)
                if (media.device.isNotBlank()) Text(media.device)
                Text("${stamp(row.start)} — ${stamp(row.end)}")
                Text("Tempo ativo · ${duration(row.watched)}")
                Text("Tempo conectado · ${duration(row.connected)}")
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Fechar") } },
        dismissButton = { TextButton(onClick = remove) { Text("Excluir sessão") } }
    )
}

@Composable internal fun ChronicleScreen(padding: PaddingValues){
    val c=LocalContext.current;val h=remember{History.get(c)};val prefs=remember{Settings.prefs(c)};val scope=rememberCoroutineScope()
    var enabled by remember{mutableStateOf(prefs.getBoolean("chron.enabled",false))}
    var retention by remember{mutableIntStateOf(prefs.getInt("chron.days",30))}
    var search by rememberSaveable{mutableStateOf("")};var category by rememberSaveable{mutableStateOf("Todas")};var device by rememberSaveable{mutableStateOf("Todos")}
    var period by rememberSaveable{mutableIntStateOf(30)};var scale by rememberSaveable{mutableStateOf("Dia")}
    var limit by rememberSaveable(category, search, device, period){mutableIntStateOf(25)}
    var remove by rememberSaveable{mutableStateOf<Long?>(null)}
    var details by rememberSaveable{mutableStateOf<Long?>(null)}
    var json by rememberSaveable{mutableStateOf(true)};var status by remember{mutableStateOf("")}
    val rev by h.revision.collectAsState();val all by produceState(emptyList<HistoryRow>(),rev){value=h.rows()}
    val today=LocalDate.now()
    val selected=remember(all,search,device,category){all.filter{(device=="Todos"||device==it.media.device)&&(category=="Todas"||chronicleCategory(it.media)==category)&&(it.media.name+" "+it.media.episode).contains(search,true)}}
    val since=today.minusDays(period-1L)
    val rows=remember(selected,period,today){selected.filter{period==0 || !day(it.start).isBefore(since)}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->if(uri!=null)scope.launch{
        status=try{withContext(Dispatchers.IO){requireNotNull(c.contentResolver.openOutputStream(uri,"wt")).bufferedWriter().use{w->
            val keys=listOf("name","episode","cover","source","device","category","startMs","endMs","activeMs","connectedMs")
            w.write(if(json)"[" else keys.joinToString(",")+"\n")
            rows.forEachIndexed{i,r->val m=r.media;val values=listOf(m.name,m.episode,m.cover,m.source,m.device,m.category,r.start,r.end,r.watched,r.connected)
                if(json){if(i>0)w.write(",");val o=JSONObject();keys.zip(values).forEach{(k,v)->o.put(k,v)};w.write(o.toString())}else w.write(values.joinToString(","){csvCell(it.toString())}+"\n")}
            if(json)w.write("]")
        }};"Exportado"}catch(e:CancellationException){throw e}catch(e:Exception){"Falha na exportação"}
    }}
    val periods = listOf("7", "30", "90", "365", "Ilimitado")
    val groups = remember(rows, limit) { rows.take(limit).groupBy { day(it.start) } }
    val devices = remember(all) { listOf("Todos") + all.map { it.media.device }.distinct() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(136.dp), modifier = Modifier.fillMaxSize(), contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(GenesisGap),
        horizontalArrangement = Arrangement.spacedBy(GenesisGap)
    ) {
        item("search", span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(search, { search = it }, label = { Text("Buscar no Chronicle") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item("categories", span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chronicleTabs.forEach { tab ->
                    FilterChip(selected = category == tab, onClick = { category = tab }, label = { Text(tab) })
                }
            }
        }
        item("filters", span = { GridItemSpan(maxLineSpan) }) {
            ChronicleDisclosure("Filtros e preferências") {
                SettingToggle("Registrar atividades neste dispositivo", enabled) {
                    enabled = it
                    prefs.edit().putBoolean("chron.enabled", it).apply()
                }
                picker("Retenção", periods, if (retention == 0) "Ilimitado" else retention.toString()) {
                    retention = it.toIntOrNull() ?: 0
                    prefs.edit().putInt("chron.days", retention).apply()
                    h.maintain()
                }
                picker("Período (dias)", periods, if (period == 0) "Ilimitado" else period.toString()) {
                    period = it.toIntOrNull() ?: 0
                }
                picker("Dispositivo", devices, device) { device = it }
            }
        }
        if (rows.isEmpty()) item("empty", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(if (!enabled) "Histórico desativado" else "Nenhuma atividade",
                    style = MaterialTheme.typography.titleMedium)
                if (!enabled) TextButton(onClick = {
                    enabled = true
                    prefs.edit().putBoolean("chron.enabled", true).apply()
                }) { Text("Ativar histórico") }
            }
        }
        groups.forEach { (date, sessions) ->
            item("day-$date", span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(when (date) {
                    today -> "Hoje"
                    today.minusDays(1) -> "Ontem"
                    else -> date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                })
            }
            items(sessions, key = { it.id }, contentType = { "activity" }) { row ->
                ChronicleTile(row) { details = row.id }
            }
        }
        if (rows.size > limit) item("more", span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = { limit += 25 }) { Text("Mostrar mais") }
        }
        item("insights", span = { GridItemSpan(maxLineSpan) }) {
            ChronicleDisclosure("Insights") {
                ChronicleInsights(rows, selected, today, since, period, scale) { scale = it }
            }
        }
        item("management", span = { GridItemSpan(maxLineSpan) }) {
            ChronicleDisclosure("Gerenciar histórico") {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("JSON", "CSV").forEach { format ->
                        TextButton(onClick = {
                            json = format == "JSON"
                            export.launch("Chronicle." + format.lowercase())
                        }) { Text("Exportar $format") }
                    }
                }
                TextButton(onClick = { remove = -1 }) { Text("Limpar histórico completo") }
                if (status.isNotEmpty()) Text(status)
            }
        }
    }
    all.firstOrNull { it.id == details }?.let { row ->
        ChronicleDetails(row, close = { details = null }, remove = { details = null; remove = row.id })
    }
    remove?.let{id->AlertDialog(onDismissRequest={remove=null},title={Text(if(id<0)"Apagar todo o histórico?" else "Apagar esta sessão?")},confirmButton={TextButton(onClick={remove=null;scope.launch{h.remove(id.takeIf{it>=0})}}){Text("Apagar")}},dismissButton={TextButton(onClick={remove=null}){Text("Cancelar")}})}
}

@Composable
private fun ChronicleInsights(
    rows: List<HistoryRow>, selected: List<HistoryRow>, today: LocalDate, since: LocalDate,
    period: Int, scale: String, onScale: (String) -> Unit
) {
    val groups = remember(rows, scale) {
        rows.groupBy {
            val date = day(it.start)
            when (scale) {
                "Semana" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                "Mês" -> date.withDayOfMonth(1)
                else -> date
            }
        }.mapValues { bucket -> bucket.value.sumOf { it.watched } }.toSortedMap()
    }
    val total = rows.sumOf { it.watched }
    val custom = rows.filter { it.media.source == "RPC Studio" || it.media.category == "Custom RPC" }
        .sumOf { it.watched }
    val days = rows.map { day(it.start) }.toSet()
    var cursor = if (today in days) today else today.minusDays(1)
    var streak = 0
    while (cursor in days) { streak++; cursor = cursor.minusDays(1) }
    Text(duration(total), style = MaterialTheme.typography.headlineMedium, fontFamily = Mono)
    Text("${rows.size} sessões · $streak dia(s) em sequência", style = MaterialTheme.typography.bodySmall)
    picker("Agrupar por", listOf("Dia", "Semana", "Mês"), scale, onScale)
    val peak = groups.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    groups.entries.toList().takeLast(12).forEach { (date, time) ->
        Text("$date · ${duration(time)}", style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(progress = { time.toFloat() / peak }, modifier = Modifier.fillMaxWidth())
    }
    Text("Reprodução · ${duration(total - custom)}", style = MaterialTheme.typography.bodySmall)
    Text("Custom RPC · ${duration(custom)}", style = MaterialTheme.typography.bodySmall)
    if (period > 0) {
        val previous = selected.filter { day(it.start) < since && day(it.start) >= since.minusDays(period.toLong()) }
            .sumOf { it.watched }
        Text("Período anterior · ${duration(previous)}", style = MaterialTheme.typography.bodySmall)
    }
    fun top(values: List<String>) = values.filter(String::isNotBlank).groupingBy { it }.eachCount().entries
        .sortedByDescending { it.value }.take(5).joinToString { "${it.key} (${it.value})" }.ifEmpty { "—" }
    Text("Mais frequentes · " + top(rows.map { it.media.name }), style = MaterialTheme.typography.bodySmall)
    Text("Dispositivos · " + top(rows.map { it.media.device }), style = MaterialTheme.typography.bodySmall)
    Text("Categorias · " + top(rows.map { chronicleCategory(it.media) }), style = MaterialTheme.typography.bodySmall)
}
