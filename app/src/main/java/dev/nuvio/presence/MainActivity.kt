package dev.nuvio.presence

import dev.nuvio.presence.ui.eclipse.*
import dev.nuvio.presence.ui.themes.*
import dev.nuvio.presence.ui.renaissance.*
import dev.nuvio.presence.ui.genesis.*
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

open class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){
        val launchSplash = installSplashScreen()
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        dev.nuvio.presence.ui.eclipse.configureEclipseSplash(launchSplash)
        NativeRpc.prepare(this)
        if(this is ControlActivity && savedInstanceState==null && intent.action=="toggle"){
            if(Monitor.state.value.running)stopMonitor()
            else if(NativeRpc.ready && (NuvioMobileProvider.allowed(this)||DeviceStore.load(this)!=null)){
                requestPresenceNotificationPermission()
                startForegroundService(Intent(this,PresenceService::class.java).setAction(PresenceService.START_AUTO))
            }
        }
        if(this !is ControlActivity && NativeRpc.ready && !Monitor.state.value.running){
            val resume=Settings.prefs(this).getString("echo.resume",null)
            if(resume in listOf(PresenceService.START,PresenceService.START_AUTO,PresenceService.START_MOBILE))
                runCatching{startForegroundService(Intent(this,PresenceService::class.java).setAction(resume))}
        }
        if(Settings.prefs(this).contains("chron.enabled"))History.get(this).maintain()
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
    private fun stopMonitor(force:Boolean=false){
        if(Monitor.state.value.running){
            startService(Intent(this,PresenceService::class.java).setAction(PresenceService.STOP))
        }else if(force){
            if(!NativeRpc.ready){Monitor.update{it.copy(discord=NativeRpc.error)};return}
            requestPresenceNotificationPermission()
            try{startForegroundService(Intent(this,PresenceService::class.java).setAction(PresenceService.STOP))}
            catch(e:Exception){Monitor.update{it.copy(discord="Falha ao iniciar limpeza RPC")}}
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun NuvioApp(){
        val ctx=LocalContext.current;val prefs=remember{Settings.prefs(ctx)};val scope=rememberCoroutineScope()
        val dashboard by Monitor.state.collectAsStateWithLifecycle()
        var paired by remember{mutableStateOf(DeviceStore.load(ctx))}
        var tab by rememberSaveable{mutableIntStateOf(if(this@MainActivity is ControlActivity && (intent.action=="devices" || (intent.action=="toggle" && DeviceStore.load(ctx)==null && !NuvioMobileProvider.allowed(ctx)))) 1 else 0)}
        var updateVisible by rememberSaveable { mutableStateOf(false) }
        var theme by remember{mutableStateOf(ThemeId.parse(prefs.getString("meta_theme",null)))}
        val freshInstall = remember {
            !prefs.contains("meta_theme") && !prefs.getBoolean("renaissance.migrated", false) &&
                prefs.all.isEmpty() && paired == null && this@MainActivity !is ControlActivity
        }
        var onboarding by rememberSaveable {
            mutableStateOf(freshInstall && !prefs.getBoolean("genesis.onboarded", false))
        }
        fun completeOnboarding() {
            onboarding = false
            prefs.edit().putBoolean("genesis.onboarded", true).apply()
        }
        var selected by remember{mutableStateOf(prefs.getString("aurora.selected",null))}
        val themeDraft = remember { ThemeDraftState() }
        var revision by remember{mutableIntStateOf(0)}
        var skins by remember{mutableStateOf(emptyList<SavedSkin>())}
        LaunchedEffect(revision){skins=withContext(Dispatchers.IO){ThemeStore(ctx).list()}}
        val custom=skins.firstOrNull{it.id==selected}
        var graphics by remember{mutableStateOf(GraphicsMode.parse(prefs.getString("meta_graphics",null)))}
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
        val startSource:(String)->Unit={mode->
            when(mode){
                "tv" -> if(paired!=null)startMonitor() else tab=1
                "mobile","auto" -> {
                    if(!NuvioMobileProvider.allowed(ctx)&&(mode=="mobile"||(paired==null&&prefs.getString("hz.studio","").isNullOrBlank()))){
                        ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }else{
                        requestPresenceNotificationPermission()
                        val action=if(mode=="mobile")PresenceService.START_MOBILE else PresenceService.START_AUTO
                        ctx.startForegroundService(Intent(ctx,PresenceService::class.java).setAction(action))
                    }
                }
            }
        }
        val layout = PaddingValues(horizontal = theme.spacing.dp, vertical = GenesisGap)
        CompositionLocalProvider(
            LocalSkin provides (custom?.skin ?: if (theme in setOf(ThemeId.AURORA, ThemeId.NEBULA, ThemeId.ECHO)) officialSkin(theme) else null),
            LocalBackground provides custom?.background
        ) {
        MetamorphosisTheme(theme,graphics){
            val light = MaterialTheme.colorScheme.background.luminance() > .45f
            SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }
            UpdateCenter(ctx, updateVisible) { updateVisible = it }
            if (onboarding) GenesisOnboarding(
                onExtensions = { completeOnboarding(); tab = 9 },
                onDismiss = ::completeOnboarding
            )
            GenesisShell(tab, { tab = it }) { activeTab ->
                if (activeTab == 8) {
                    ChronicleScreen(layout)
                } else if (activeTab == 9) {
                    IntegrationHub(dashboard, layout)
                } else if (activeTab == 5) {
                    RpcStudio(dashboard, layout, onPublish = { preset ->
                        requestPresenceNotificationPermission()
                        try {
                            startForegroundService(Intent(this@MainActivity, PresenceService::class.java)
                                .setAction(PresenceService.START_STUDIO)
                                .putExtra(PresenceService.STUDIO_JSON, RpcStore.encode(preset)))
                        } catch (_: Exception) {
                            Monitor.update { it.copy(discord = "Falha ao iniciar RPC Studio") }
                        }
                    }, onStop = { stopMonitor(force = true) })
                } else if (activeTab == 6) {
                    ThemeStudio(
                        skins, selected, theme, graphics, layout, themeDraft,
                        apply = { base, id ->
                            theme = base
                            selected = id
                            prefs.edit().putString("meta_theme", base.name).putString("aurora.selected", id).apply()
                        },
                        refresh = { revision++ },
                        onMode = { graphics = it; prefs.edit().putString("meta_graphics", it.name).apply() }
                    )
                } else Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(layout),
                    verticalArrangement = Arrangement.spacedBy(GenesisGap)
                ) {
                    when(activeTab) {
                        0 -> GenesisHome(
                            dashboard, now, options, NativeRpc.ready, paired != null,
                            onDevices = { tab = 1 }, onStudio = { tab = 5 },
                            onStop = { stopMonitor() }, onStart = startSource
                        )
                        1->{
                            var ip by rememberSaveable{mutableStateOf(paired?.ip?:"")};var fingerprint by remember{mutableStateOf("")};var code by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var feedback by remember{mutableStateOf("")}
                            var manualOpen by rememberSaveable{mutableStateOf(false)}
                            var installOpen by rememberSaveable{mutableStateOf(false)}
                            var installing by remember{mutableStateOf(false)}
                            PairQrButton(
                                    onScanned = { qrIp, qrIdentity, qrCode ->
                                        ip = qrIp
                                        fingerprint = qrIdentity
                                        code = qrCode
                                        feedback = "QR lido. Confirme a identidade e o código."
                                        manualOpen = true
                                    },
                                    onError = { feedback = it; manualOpen = true },
                                    enabled = !busy && !installing && !dashboard.running
                            )
                            DeviceHub(paired,dashboard.running,{ip=it;manualOpen=true},{paired=DeviceStore.load(ctx)},
                                ready=NativeRpc.ready,onConnect={startMonitor()},onStop={stopMonitor()})
                            ClarityDrawer("Pareamento manual",manualOpen,{manualOpen=!manualOpen},enabled=!busy&&!installing){
                                Text("Gere o código na TV e confira a identidade.",style=MaterialTheme.typography.bodySmall)
                                OutlinedTextField(ip,{ip=it},label={Text("IP da Fire TV")},singleLine=true,enabled=!busy&&!dashboard.running,modifier=Modifier.fillMaxWidth())
                                OutlinedTextField(fingerprint,{fingerprint=it.take(64)},label={Text("Identidade (16 ou 64 caracteres)")},singleLine=true,enabled=!busy&&!dashboard.running,modifier=Modifier.fillMaxWidth())
                                OutlinedTextField(code,{code=it.filter{c->c.isDigit()}.take(8)},label={Text("Código temporário")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,enabled=!busy&&!dashboard.running,modifier=Modifier.fillMaxWidth())
                                GlassButton(onClick={busy=true;feedback="Pareando…";scope.launch{try{val d=withContext(Dispatchers.IO){LanClient.pair(ip,fingerprint,code).also{DeviceStore.save(ctx,it)}};paired=d;code="";fingerprint="";feedback="Pareamento salvo. Vá para Início e sincronize.";Monitor.log("Fire TV pareada")}catch(e:CancellationException){throw e}catch(e:Exception){feedback=when(e.message){"Código incorreto","Código expirado ou bloqueado","Informe um IPv4 válido","Use o IP privado da Fire TV","Identidade inválida","O código possui 8 dígitos"->e.message!!;else->"Falha de conexão ou identidade. Confira os dados e o coletor."}}finally{busy=false}}},enabled=!busy&&!installing&&!dashboard.running&&ip.isNotBlank()&&fingerprint.length>=16&&code.length==8,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Conectando…" else "Parear dispositivo")}
                                if(feedback.isNotEmpty())Text(feedback,style=MaterialTheme.typography.bodyMedium)
                            }
                            ClarityDrawer("Instalar Collector",installOpen,{installOpen=!installOpen},enabled=!installing){
                                CollectorSetup(ip, { ip=it }, dashboard.running || busy, onReset={DeviceStore.ids(ctx).firstOrNull{DeviceStore.get(ctx,it)?.ip==ip.trim()}?.let{DeviceStore.remove(ctx,it)};paired=DeviceStore.load(ctx)},onBusyChange={installing=it})
                            }
                        }
                        2->{TextButton(onClick={tab=3}){Text("Voltar aos ajustes")};EclipseHeading("Rich Presence", "O que aparece no Discord");PrismEditor(prefs,dashboard.playback,dashboard.connection.substringBefore(" •"),now,options);Text(if(NativeRpc.ready)dashboard.discord else NativeRpc.error)
                            SettingToggle("Limpar presença ao pausar",options.clearPaused){options=options.copy(clearPaused=it);prefs.edit().putBoolean("clearPaused",it).apply()}
                            SettingToggle("Mostrar capa",options.artwork){options=options.copy(artwork=it);prefs.edit().putBoolean("art",it).apply()}
                            SettingToggle("Buscar capas no Mobile",mobilePosterSearch){mobilePosterSearch=it;prefs.edit().putBoolean("mobilePosterSearch",it).apply()}
                            Text("Se a capa estiver indisponível, envia o título ao Cinemeta (Stremio). Não envia arquivos, links de reprodução ou credenciais. Resultados ambíguos são ignorados.",style=MaterialTheme.typography.bodySmall)
                            SettingToggle("Mostrar nome do episódio",options.episode){options=options.copy(episode=it);prefs.edit().putBoolean("episode",it).apply()}
                            SettingToggle("Mostrar progresso",options.timestamps){options=options.copy(timestamps=it);prefs.edit().putBoolean("time",it).apply()}
                            SettingToggle("Tipo Assistindo (Watching)",options.watching){options=options.copy(watching=it);prefs.edit().putBoolean("watch",it).apply()}
                            Text("Desative Watching se o Discord recusar esse tipo. As mudanças são aplicadas na próxima sincronização.",style=MaterialTheme.typography.bodySmall)
                            GlassOutlinedButton(onClick={stopMonitor(force=true)},enabled=dashboard.connection!="Encerrando"){Text("Limpar presença e parar")}
                        }
                        3->{RenaissanceSettings(
                            version=ctx.packageManager.getPackageInfo(ctx.packageName,0).versionName.orEmpty(),
                            interval=interval,onInterval={interval=it;prefs.edit().putInt("interval",it).apply()},
                            onPresence={tab=2},onThemes={tab=6},onUpdates={updateVisible=true},onDiagnostics={tab=4},onAbout={tab=7}
                        )}
                        7->{EclipseHeading("SukiRPC", "Genesis");ThemePanel(Modifier.fillMaxWidth()){Text("Versão ${ctx.packageManager.getPackageInfo(ctx.packageName,0).versionName}");Text("Discord Social SDK • Android");TextButton(onClick={updateVisible=true}){Text("Verificar atualizações")}}}
                        4->{
                            ArtworkDiagnostics(dashboard, options)
                            Diagnostics(dashboard);TextButton(onClick={tab=3}){Text("Voltar aos ajustes")}}
                    }
                }
            }
        }
        }
    }
}



