package dev.nuvio.presence

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.saveable.rememberSaveable
import dev.nuvio.presence.ui.renaissance.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.nuvio.presence.ui.themes.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.DateFormat

private fun date(t:Long)=if(t>0)DateFormat.getDateTimeInstance().format(java.util.Date(t)) else "Ainda não medido"
@Composable internal fun DeviceHub(current:PairedDevice?,running:Boolean,useIp:(String)->Unit,changed:()->Unit,
    ready:Boolean,onConnect:()->Unit,onStop:()->Unit){
    val c=LocalContext.current;val prefs=remember{Settings.prefs(c)}
    var revision by remember{mutableIntStateOf(0)}
    val ids=remember(current,revision){DeviceStore.ids(c)}
    var discovery by remember{mutableStateOf(false)}
    var searchOpen by rememberSaveable{mutableStateOf(false)}
    var advanced by rememberSaveable{mutableStateOf(false)}
    var expanded by rememberSaveable{mutableStateOf<String?>(null)}
    var peers by remember{mutableStateOf(emptyList<EchoPeer>())}
    var status by remember{mutableStateOf("")}
    val samples by EchoHealth.links.collectAsState()
    val errors by EchoHealth.errors.collectAsState()
    val scope=rememberCoroutineScope()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")){uri->if(uri!=null)scope.launch{
        status=try{withContext(Dispatchers.IO){val f=File.createTempFile("echo",".apk",c.cacheDir);try{verifiedCollector(c,f);f.inputStream().use{input->requireNotNull(c.contentResolver.openOutputStream(uri)).use{input.copyTo(it)}}}finally{f.delete()}};"APK exportado"}catch(e:CancellationException){throw e}catch(e:Exception){"Falha ao exportar APK"}
    }}
    LaunchedEffect(discovery){
        if(discovery){
            peers=emptyList()
            try{withTimeoutOrNull(12000){(c as LifecycleOwner).lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED){discover(c).collect{peers=it}}}}
            catch(e:CancellationException){throw e}catch(e:Exception){status="Busca indisponível"}
            discovery=false
        }
    }
    if(ids.isEmpty()){
        ThemePanel(Modifier.fillMaxWidth()){
            Text("Nenhum dispositivo",style=MaterialTheme.typography.titleLarge)
        }
    }
    ids.sortedBy{if(it==current?.pin)0 else 1}.forEach{pin->key(pin){
        var name by remember{mutableStateOf(prefs.getString("echo.name."+pin,"Collector").orEmpty())}
        var savedName by remember{mutableStateOf(name)}
        var favorite by remember{mutableStateOf(prefs.getBoolean("echo.fav."+pin,false))}
        var confirm by remember{mutableStateOf(false)}
        val last=samples[pin];val version=last?.version?:prefs.getString("echo.version."+pin,"Não consultado")
        val selected=current?.pin==pin
        val found=discovery&&peers.any{it.pin==pin}
        val label = when {
            found -> "Na rede"
            errors[pin] != null -> "Falha de conexão"
            selected -> "Pareado · prioritário"
            else -> "Pareado"
        }
        SpatialDeviceCard(savedName.ifBlank{"Collector"},label,selected||found,expanded==pin,{expanded=if(expanded==pin)null else pin}){
            if(selected){
                SoftAction(if(running)"Parar RPC" else "Conectar",if(running)Icons.Filled.Close else Icons.Filled.PlayArrow,
                    if(running)onStop else onConnect,Modifier.weight(1f),enabled=running||ready)
            }else{
                SoftAction("Priorizar",Icons.Filled.Star,{DeviceStore.select(c,pin);changed();revision++},Modifier.weight(1f),enabled=!running)
            }
        }
        if(expanded==pin){
            ThemePanel(Modifier.fillMaxWidth()){
                OutlinedTextField(name,{name=it.take(48)},label={Text("Nome")},singleLine=true,modifier=Modifier.fillMaxWidth())
                TextButton(onClick={savedName=name.trim().ifBlank{"Collector"};prefs.edit().putString("echo.name."+pin,savedName).apply();status="Nome salvo"}){Text("Salvar nome")}
                SettingToggle("Favorito",favorite){favorite=it;prefs.edit().putBoolean("echo.fav."+pin,it).apply()}
                val device=remember(pin,revision){DeviceStore.get(c,pin)}
                Text("IP: "+(device?.ip?:"Indisponível"),style=MaterialTheme.typography.bodySmall)
                Text("Versão: $version · Último contato: "+date(last?.synced?:prefs.getLong("echo.last."+pin,0)),style=MaterialTheme.typography.bodySmall)
                Text(errors[pin]?:if(found)"Disponível na busca" else "Disponibilidade atual não confirmada",style=MaterialTheme.typography.bodySmall)
                if(version=="Legado")Text("Atualize para o Collector 2.0.0 para habilitar descoberta.",style=MaterialTheme.typography.bodySmall)
                TextButton(enabled=!running,onClick={confirm=true}){Text("Remover pareamento",color=MaterialTheme.colorScheme.error)}
            }
        }
        if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Remover este pareamento?")},confirmButton={TextButton(onClick={DeviceStore.remove(c,pin);changed();revision++;confirm=false}){Text("Remover")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancelar")}})
    }}
    ClarityDrawer("Buscar na rede",searchOpen,{searchOpen=!searchOpen},enabled=!discovery){
        SoftAction(if(discovery)"Encerrar busca" else "Buscar dispositivos",Icons.Filled.Search,{discovery=!discovery},Modifier.fillMaxWidth())
        if(discovery)LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Confira a identidade na TV antes de parear.",style=MaterialTheme.typography.bodySmall)
        peers.forEach{p->TextButton(onClick={useIp(p.ip)},enabled=!running){
            Text("${p.name} · ${p.model}\n${p.ip} · ${p.version} · "+(if(p.protocol in 1..2)"Compatível" else "Incompatível")+(if(p.pin in ids)" · Pareado" else ""))
        }}
    }
    ClarityDrawer("Opções avançadas",advanced,{advanced=!advanced}){
        var policy by remember{mutableStateOf(prefs.getString("echo.policy","Automatic"))}
        Text("Prioridade no modo Auto",style=MaterialTheme.typography.titleMedium)
        val policies=listOf("Automatic" to "Automático","Mobile Priority" to "Mobile","TV Priority" to "TV","Manual" to "Manual")
        Row(Modifier.horizontalScroll(rememberScrollState())){policies.forEach{(mode,label)->FilterChip(selected=policy==mode,onClick={policy=mode;prefs.edit().putString("echo.policy",mode).apply()},label={Text(label)})}}
        if(policy=="Manual"){var mobile by remember{mutableStateOf(prefs.getBoolean("echo.manualMobile",false))};SettingToggle("Priorizar Mobile",mobile){mobile=it;prefs.edit().putBoolean("echo.manualMobile",it).apply()}}
        TextButton(onClick={export.launch("SukiRPC-Collector-2.0.0.apk")}){Text("Exportar Collector")}
        Text("Instale na TV e autorize o acesso às notificações.",style=MaterialTheme.typography.bodySmall)
    }
    if(status.isNotEmpty())Text(status,style=MaterialTheme.typography.bodySmall)
}
@Composable internal fun Diagnostics(d:Dashboard){
    val c=LocalContext.current;val prefs=remember{Settings.prefs(c)}
    val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    var level by remember{mutableStateOf("Todos")};var area by remember{mutableStateOf("Todas")}
    var preview by remember{mutableStateOf<String?>(null)}
    val samples by EchoHealth.links.collectAsState();val errors by EchoHealth.errors.collectAsState();val attempts by EchoHealth.failures.collectAsState()
    val device=DeviceStore.load(c);val sample=samples[device?.pin]
    val cm=c.getSystemService(ConnectivityManager::class.java)
    fun lan():Boolean=cm.getNetworkCapabilities(cm.activeNetwork)?.let{it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)}==true
    ThemePanel(Modifier.fillMaxWidth()){
        Text("Diagnóstico Echo",style=MaterialTheme.typography.titleLarge)
        Text("Android ${Build.VERSION.RELEASE} · ${Build.SUPPORTED_ABIS.firstOrNull()} · SukiRPC ${c.packageManager.getPackageInfo(c.packageName,0).versionName}")
        Text("Discord: ${d.discord}\nFonte: ${d.connection}\nRede local: ${if(lan())"disponível" else "não identificada"}")
        Text("Pareamento: ${if(device!=null)"salvo" else "ausente"} · Mobile: ${if(NuvioMobileProvider.allowed(c))"autorizado" else "sem acesso"}")
        Text(errors[device?.pin]?:if(sample==null)"Autenticação ainda não medida" else "Última resposta autenticada · protocolo ${sample.protocol} · Collector ${sample.version} · ida e volta ${sample.latency} ms")
        Text("Última sincronização: ${date(sample?.synced?:0)} · Falhas nesta sessão: $attempts")
        TextButton(onClick={message=if(lan())"Wi-Fi ou Ethernet disponível" else "Conecte-se à rede do Collector"}){Text("Verificar rede")}
        TextButton(onClick={message=if(NativeRpc.ready)"SDK inicializado · ${d.discord}" else "SDK indisponível · abra o Discord"}){Text("Verificar SDK local")}
        TextButton(enabled=!busy && !d.running && device!=null,onClick={busy=true;scope.launch{try{withContext(Dispatchers.IO){EchoHealth.read(c,device!!)};message="Collector autenticado"}catch(e:Exception){message=EchoHealth.error(e)}finally{busy=false}}}){Text("Testar TLS e autenticação")}
        Text(message)
    }
    var verbose by remember{mutableStateOf(prefs.getBoolean("echo.verbose",false))}
    SettingToggle("Registrar amostras autenticadas (detalhado)",verbose){verbose=it;prefs.edit().putBoolean("echo.verbose",it).apply()}
    Text("Eventos · até 80 na memória")
    Row(Modifier.horizontalScroll(rememberScrollState())){listOf("Todos","Info","Aviso","Erro").forEach{l->TextButton(onClick={level=l}){Text(if(level==l)"✓ $l" else l)}}}
    Row(Modifier.horizontalScroll(rememberScrollState())){(listOf("Todas")+Event.entries.map{it.area}.distinct()).forEach{a->TextButton(onClick={area=a}){Text(if(area==a)"✓ $a" else a)}}}
    val lines=d.logs.filter{(level=="Todos"||" $level ·" in it)&&(area=="Todas"||" · $area ·" in it)}
    lines.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
    TextButton(onClick={preview="SukiRPC Echo 1.3.0\nEventos: ${lines.size}\n"+report(lines)}){Text("Prévia do relatório")}
    preview?.let{safe->Text(safe);TextButton(onClick={c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,safe),null))}){Text("Compartilhar relatório")};TextButton(onClick={preview=null}){Text("Fechar prévia")}}
}
