package dev.nuvio.presence

import dev.nuvio.presence.ui.eclipse.*
import dev.nuvio.presence.updater.UpdateCenter

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        val launchSplash = installSplashScreen()
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        dev.nuvio.presence.ui.eclipse.configureEclipseSplash(launchSplash)
        NativeRpc.prepare(this)
        setContent{NuvioApp()}
    }
    private fun requestPresenceNotificationPermission(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),10)
    }
    private fun startMonitor(){
        if(!NativeRpc.ready){Monitor.update{it.copy(discord=NativeRpc.error)};return}
        requestPresenceNotificationPermission()
        try{startForegroundService(Intent(this,PresenceService::class.java).setAction(PresenceService.START))}catch(e:Exception){Monitor.log("Não foi possível iniciar o serviço");Monitor.update{it.copy(discord="Falha ao iniciar serviço")}}
    }
    private fun stopMonitor(){if(Monitor.state.value.running)startService(Intent(this,PresenceService::class.java).setAction(PresenceService.STOP))}
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun NuvioApp(){
        val ctx=LocalContext.current;val prefs=remember{Settings.prefs(ctx)};val scope=rememberCoroutineScope()
        val dashboard by produceState(Monitor.state.value){
            this@MainActivity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
                Monitor.state.collect{value=it}
            }
        };var paired by remember{mutableStateOf(DeviceStore.load(ctx))}
        var tab by rememberSaveable{mutableIntStateOf(0)}
        var updateVisible by rememberSaveable { mutableStateOf(false) }
        var theme by remember{mutableStateOf(prefs.getString("theme","system")?:"system")}
        var dynamic by remember{mutableStateOf(prefs.getBoolean("dynamic",true))}
        var options by remember{mutableStateOf(Settings.options(ctx))}
        var mobilePosterSearch by remember{mutableStateOf(prefs.getBoolean("mobilePosterSearch",false))}
        var interval by remember{mutableIntStateOf(prefs.getInt("interval",2).coerceIn(1,5))}
        var now by remember{mutableLongStateOf(SystemClock.elapsedRealtime())}
        LaunchedEffect(tab,dashboard.running,dashboard.playback?.state,dashboard.playback?.speed,options.timestamps){
            this@MainActivity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
                now=SystemClock.elapsedRealtime()
                if((tab==0||tab==2)&&dashboard.running&&options.timestamps&&dashboard.playback?.state=="playing"&&(dashboard.playback?.speed?:0.0)>0){
                    while(isActive){delay(1000);now=SystemClock.elapsedRealtime()}
                }
            }
        }
        val dark=theme=="dark"||theme=="oled"||(theme=="system"&&androidx.compose.foundation.isSystemInDarkTheme())
        val colors=when{theme=="oled"->darkColorScheme(primary=Color(0xffc4b5fd),background=Color.Black,surface=Color.Black,surfaceContainer=Color(0xff111116));dynamic&&Build.VERSION.SDK_INT>=31&&dark->dynamicDarkColorScheme(ctx);dynamic&&Build.VERSION.SDK_INT>=31->dynamicLightColorScheme(ctx);dark->darkColorScheme(primary=Color(0xffc4b5fd),secondary=Color(0xffccc0e8));else->lightColorScheme(primary=Color(0xff6750a4),secondary=Color(0xff625b71))}
        EclipseTheme(colorScheme=colors){
            UpdateCenter(ctx, updateVisible) { updateVisible = it }
            Scaffold(topBar={CenterAlignedTopAppBar(title={Text(if(tab==4)"Diagnóstico" else "SukiRPC",fontWeight=FontWeight.SemiBold)})},bottomBar={EclipseNavigation(tab) { tab = it }}){padding->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                    EclipsePage(tab) { activeTab ->
                    when(activeTab){
                        0->{
                            EclipseHome(
                                playback = dashboard.playback,
                                now = now,
                                options = options,
                                running = dashboard.running,
                                connection = dashboard.connection,
                                discord = if (NativeRpc.ready) dashboard.discord else NativeRpc.error,
                                ready = NativeRpc.ready,
                                paired = paired != null,
                                onDevices = { tab = 1 },
                                onStop = { stopMonitor() },
                                onStart = { mode ->
                                    when (mode) {
                                        "tv" -> if (paired != null) startMonitor() else tab = 1
                                        "mobile", "auto" -> {
                                            if (!NuvioMobileProvider.allowed(ctx) && (mode == "mobile" || paired == null)) {
                                                ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                            } else {
                                                val action = if (mode == "mobile") PresenceService.START_MOBILE else PresenceService.START_AUTO
                                                requestPresenceNotificationPermission()
                                                ctx.startForegroundService(android.content.Intent(ctx, PresenceService::class.java).setAction(action))
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        1->{
                            EclipseHeading("Dispositivos", "Pareamento e configuração da TV")
                            Text("Conexão local criptografada. Pareie uma vez e o aplicativo lembra a TV.")
                            if(paired!=null){ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                Text("Dispositivo pareado",style=MaterialTheme.typography.titleMedium);Text(paired!!.ip+":8766");Text(dashboard.connection)
                                Button(onClick={startMonitor()},enabled=!dashboard.running&&NativeRpc.ready){Text("Conectar e sincronizar")}
                                OutlinedButton(onClick={stopMonitor()},enabled=dashboard.running){Text("Desconectar")}
                                TextButton(onClick={stopMonitor();DeviceStore.clear(ctx);paired=null;Monitor.log("Pareamento removido do celular")}){Text("Esquecer dispositivo")}
                            }}}
                            var ip by rememberSaveable{mutableStateOf(paired?.ip?:"")};var fingerprint by remember{mutableStateOf("")};var code by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var feedback by remember{mutableStateOf("")}
                            CollectorSetup(ip, { ip=it }, dashboard.running || busy, onReset={DeviceStore.clear(ctx);paired=null})
                            ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                                Text("Novo pareamento",style=MaterialTheme.typography.titleLarge)
                                PairQrButton(
                                    onScanned = { qrIp, qrIdentity, qrCode ->
                                        ip = qrIp
                                        fingerprint = qrIdentity
                                        code = qrCode
                                        feedback = "QR lido. Confirme o pareamento."
                                    },
                                    onError = { feedback = it },
                                    enabled = !busy && !dashboard.running
                                )

                                Text("Abra o SukiRPC Collector na TV e pressione Gerar código. Confira os dados exibidos na TV.",style=MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(ip,{ip=it},label={Text("IP da Fire TV")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                OutlinedTextField(fingerprint,{fingerprint=it.take(64)},label={Text("Identidade (16 ou 64 caracteres)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                OutlinedTextField(code,{code=it.filter{c->c.isDigit()}.take(8)},label={Text("Código temporário")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.fillMaxWidth())
                                Button(onClick={busy=true;feedback="Pareando…";scope.launch{try{val d=withContext(Dispatchers.IO){LanClient.pair(ip,fingerprint,code).also{DeviceStore.save(ctx,it)}};paired=d;code="";fingerprint="";feedback="Pareamento salvo. Vá para Início e sincronize.";Monitor.log("Fire TV pareada")}catch(e:CancellationException){throw e}catch(e:Exception){feedback=when(e.message){"Código incorreto","Código expirado ou bloqueado","Informe um IPv4 válido","Use o IP privado da Fire TV","Identidade inválida","O código possui 8 dígitos"->e.message!!;else->"Falha de conexão ou identidade. Confira os dados e o coletor."}}finally{busy=false}}},enabled=!busy&&!dashboard.running&&ip.isNotBlank()&&fingerprint.length>=16&&code.length==8,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Conectando…" else "Parear dispositivo")}
                                if(feedback.isNotEmpty())Text(feedback,style=MaterialTheme.typography.bodyMedium)
                            }}
                        }
                        2->{TextButton(onClick={tab=3}){Text("Voltar aos ajustes")};EclipseHeading("Rich Presence", "O que aparece no Discord");Preview(dashboard.playback,now,options);Text(if(NativeRpc.ready)dashboard.discord else NativeRpc.error)
                            SettingToggle("Mostrar capa",options.artwork){options=options.copy(artwork=it);prefs.edit().putBoolean("art",it).apply()}
                            SettingToggle("Buscar capas no Mobile",mobilePosterSearch){mobilePosterSearch=it;prefs.edit().putBoolean("mobilePosterSearch",it).apply();if(!it)MobilePosterSearch.reset()}
                            Text("Se a capa estiver indisponível, envia o título ao Cinemeta (Stremio). Não envia arquivos, links de reprodução ou credenciais. Resultados ambíguos são ignorados.",style=MaterialTheme.typography.bodySmall)
                            SettingToggle("Mostrar nome do episódio",options.episode){options=options.copy(episode=it);prefs.edit().putBoolean("episode",it).apply()}
                            SettingToggle("Mostrar progresso",options.timestamps){options=options.copy(timestamps=it);prefs.edit().putBoolean("time",it).apply()}
                            SettingToggle("Tipo Assistindo (Watching)",options.watching){options=options.copy(watching=it);prefs.edit().putBoolean("watch",it).apply()}
                            Text("Desative Watching se o Discord recusar esse tipo. As mudanças são aplicadas na próxima sincronização.",style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick={stopMonitor()},enabled=dashboard.running){Text("Limpar presença e parar")}
                        }
                        3->{EclipseHeading("Ajustes", "Personalize sua experiência");EclipseSettingsLink("Rich Presence", "Capas, episódios e progresso") { tab=2 };Text("Aparência",style=MaterialTheme.typography.titleMedium);ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){
                            listOf("system" to "Sistema","light" to "Claro","dark" to "Escuro","oled" to "OLED").forEach{(id,label)->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected=theme==id,onClick={theme=id;prefs.edit().putString("theme",id).apply()});Text(label)}}
                        }}
                            SettingToggle("Cores dinâmicas do Android",dynamic){dynamic=it;prefs.edit().putBoolean("dynamic",it).apply()}
                            Text("Reprodução",style=MaterialTheme.typography.titleLarge)
                            SettingToggle("Limpar presença ao pausar",options.clearPaused){options=options.copy(clearPaused=it);prefs.edit().putBoolean("clearPaused",it).apply()}
                            Text("Consulta durante reprodução: $interval s (modo econômico em pausa/espera)");Slider(value=interval.toFloat(),onValueChange={interval=it.toInt().coerceIn(1,5);prefs.edit().putInt("interval",interval).apply()},valueRange=1f..5f,steps=3)
                            Text("Reconexão automática durante o monitoramento. O serviço pode ser interrompido pelo Android/HyperOS.",style=MaterialTheme.typography.bodySmall)
                            EclipseSettingsLink("Atualizações", "Verificar novas versões") { updateVisible = true }; OutlinedButton(onClick={tab=4}){Text("Diagnóstico e logs")};Text("SukiRPC • ${ctx.packageManager.getPackageInfo(ctx.packageName,0).versionName}",style=MaterialTheme.typography.labelMedium)
                        }
                        4->{EclipseHeading("Diagnóstico", "Eventos recentes");Text("Sem códigos ou credenciais nos logs.",style=MaterialTheme.typography.bodySmall);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={val cm=ctx.getSystemService(android.content.ClipboardManager::class.java);cm.setPrimaryClip(android.content.ClipData.newPlainText("Nuvio logs",dashboard.logs.joinToString("\n")))}){Text("Copiar")};TextButton(onClick={Monitor.update{it.copy(logs=emptyList())}}){Text("Limpar")}};dashboard.logs.forEach{Text(it,style=MaterialTheme.typography.bodySmall);HorizontalDivider()};TextButton(onClick={tab=3}){Text("Voltar aos ajustes")}}
                    }
                        Unit
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
