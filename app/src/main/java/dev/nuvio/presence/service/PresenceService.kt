package dev.nuvio.presence

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import kotlinx.coroutines.*
import org.json.JSONObject
import dev.nuvio.artwork.ArtworkPolicy


class PresenceService:Service(){
    companion object {const val STOP="dev.nuvio.presence.STOP";const val START="dev.nuvio.presence.START";const val START_MOBILE="dev.nuvio.presence.START_MOBILE";const val START_STUDIO="dev.nuvio.presence.START_STUDIO";const val STUDIO_JSON="studio_json";const val START_AUTO="dev.nuvio.presence.START_AUTO";private const val CHANNEL="presence";private const val ID=33}
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var polling:Job?=null
    private var callbackJob:Job?=null
    private var published:Presence?=null
    private var pending:Presence?=null
    private val clearRetries=RpcClearRetries()
    private fun requestClear(){clearRetries.request(SystemClock.elapsedRealtime(),NativeRpc::nativeClear)}
    private fun tickClear(){clearRetries.tick(SystemClock.elapsedRealtime(),NativeRpc::nativeClear)}
    private var pendingAt=0L
    private var attemptAt=-100000L
    private var retryAt=0L
    private var stopping=false
    private var echo:EchoSession?=null
    private val chooser=SourceChoice()
    private var lastSource=""
    private var cleanupFinished=false
    private var sessionStarted=false
    private val history by lazy { History.get(this) }
    private var recording=false
    private fun endHistory(final:Boolean=false){if(recording){history.boundary(final);recording=false}}
    private fun record(item:HistoryMedia,playing:Boolean,now:Long){recording=true;history.sample(item,playing,now)}
    private var sourceMode="tv"
    private val integrations by lazy{Integrations(this)}
    private var currentSource="tv"
    private val artworkEngine = HorizonArtworkEngine(scope, SystemClock::elapsedRealtime, MobilePosterSearch::lookup)
    private var failureCount=0
    private var fallback:RpcPreset?=null
    private var fallbackStart=0L
    private var fallbackAttempt=-60000L
    private var cachedSelection:String?=null
    private var chosenPreset:RpcPreset?=null
    private fun releaseFallback(){
        if(fallback!=null){clearStudio();endHistory();fallback=null;Monitor.update{it.copy(studio=null,artworkForwarded=false)}}
    }
    private suspend fun autoStudio(now:Long):Boolean{
        val name=preferences.getString("hz.studio","").orEmpty()
        if(sourceMode!="auto" || name.isEmpty()){releaseFallback();return false}
        if(cachedSelection!=name){cachedSelection=name;chosenPreset=withContext(Dispatchers.IO){RpcStore.load(this@PresenceService).firstOrNull{it.name==name && it.error()==null}}}
        val preset=chosenPreset
        val eligible=preset!=null && (preset.appPackage.isEmpty() || (preset.appPackage !in integrations.blocked() &&
            withContext(Dispatchers.IO){runCatching{RpcAppDetection.matches(this@PresenceService,preset.appPackage)}.getOrDefault(false)}))
        if(!eligible || preset==null || privacy.hidden.isNotEmpty() || privacy.blocks(name) || preferences.getString("hz.studio","")!=name){releaseFallback();return false}
        if(fallback!=preset){releaseFallback();clear();fallback=preset;fallbackStart=System.currentTimeMillis();fallbackAttempt=-60000L;endHistory()}
        val end=if(preset.timer=="countdown")fallbackStart+preset.minutes*60000L else 0
        if(end>0 && System.currentTimeMillis()>=end){releaseFallback();preferences.edit().remove("hz.studio").apply();return false}
        if(studioPending){val outcome=NativeRpc.nativeOutcome()
            if(outcome==1){studioPublished=true;studioPending=false}
            else if(outcome<0 || now-fallbackAttempt>12000){clearStudio();fallbackAttempt=now-54000}
        }
        if(!studioPending && now-fallbackAttempt>=60000){
            clearRetries.cancel()
            val forwarded = NativeRpc.publishStudio(preset,if(preset.timer=="none")0 else fallbackStart,end)
            Monitor.update { it.copy(artworkForwarded = forwarded) }
            studioPending=true;fallbackAttempt=now
        }
        Monitor.update{it.copy(studio=preset,playback=null,connection="RPC Studio • Auto",artworkStatus="",discord=if(studioPublished)"Presença confirmada" else "Atualizando presença")}
        if(studioPublished && preferences.getBoolean("chron.enabled",false))record(HistoryMedia(name,"",historyCover(preset.largeImage),"RPC Studio",android.os.Build.MODEL,if(preset.type=="playing")"Jogo" else "Custom RPC"),true,now)
        return true
    }
    private var studioPublished=false
    private var studioPending=false
    private val preferences by lazy { Settings.prefs(this) }
    private var format = MediaFormat()
    private var privacy = MediaPrivacy()
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if(key?.startsWith("hz.")==true && !stopping){
            releaseFallback();cachedSelection=null
            val current=Monitor.state.value.playback
            if (current != null && (!integrations.accepts(current.appId) ||
                (current.state == "paused" && integrations.clearOnPause(current.appId)))) {
                artworkEngine.cancel()
                Monitor.manualArtwork.clear()
                clear()
                endHistory()
                Monitor.update { it.copy(playback = null, artworkStatus = "") }
            }
        }
        val changesArtwork = key == "art" || key == "mobilePosterSearch" ||
            key?.startsWith("prism.") == true || key?.startsWith("hz.art.") == true
        if (changesArtwork) {
            artworkEngine.cancel(clearCache = true)
            val current = Monitor.state.value.playback
            if (current != null && (!artworkAllowed(Settings.options(this), readPrivacy(preferences), current.title) ||
                !integrations.artwork(current.appId))) {
                Monitor.manualArtwork.clear()
                clear()
                Monitor.update { it.copy(playback = current.withoutArtwork(), artworkStatus = "Desativada") }
            }
        }
        if(key=="chron.enabled")endHistory(true)
        if (key?.startsWith("prism.") == true || key in setOf("art","time","watch","clearPaused","episode")) {
            val fresh = readPrivacy(preferences)
            val privacyChanged = fresh != privacy
            privacy = fresh
            format = readFormat(preferences.all)
            if (!stopping && polling?.isActive == true) {
                if (privacyChanged) { releaseFallback();endHistory(true);clear(); clearStudio() }
                if (sourceMode != "studio") reconcile(Monitor.state.value.playback, SystemClock.elapsedRealtime(), Settings.options(this))
            }
        }
    }
    override fun onBind(intent:Intent?)=null
    override fun onCreate(){super.onCreate();format=readFormat(preferences.all);privacy=readPrivacy(preferences);preferences.registerOnSharedPreferenceChangeListener(preferenceListener);val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel(CHANNEL,"Sincronização SukiRPC",NotificationManager.IMPORTANCE_LOW))}
    private val eclipseNotifications by lazy {
        dev.nuvio.presence.ui.eclipse.EclipseNotifications(this,scope,CHANNEL,ID,STOP)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==STOP){
            if(!stopping){
                if(polling?.isActive!=true){
                    val notification=eclipseNotifications.starting()
                    if(Build.VERSION.SDK_INT>=29)startForeground(ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)else startForeground(ID,notification)
                }
                stopMonitoring()
            }
            return START_NOT_STICKY
        }
        if(polling?.isActive==true||stopping)return START_NOT_STICKY
        if(intent?.action==START_STUDIO){
            val preset=runCatching{RpcStore.decode(intent.getStringExtra(STUDIO_JSON).orEmpty())}.getOrNull()
            val n=eclipseNotifications.starting()
            if(Build.VERSION.SDK_INT>=29)startForeground(ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)else startForeground(ID,n)
            if(preset==null||!NativeRpc.ready||(preset.appPackage.isNotEmpty()&&!RpcAppDetection.allowed(this))){
                Monitor.update{it.copy(running=false,studio=null,discord="RPC inválido, SDK indisponível ou permissão ausente")};stopSelf();return START_NOT_STICKY
            }
            startStudio(preset);return START_NOT_STICKY
        }
        if(intent==null && !preferences.contains("echo.resume")){stopSelf();return START_NOT_STICKY}
        val action=intent?.action?:preferences.getString("echo.resume",START_AUTO)
        sourceMode=when(action){START_MOBILE->"mobile";START_AUTO->"auto";else->"tv"}
        currentSource=sourceMode
        val n=eclipseNotifications.starting()
        if(Build.VERSION.SDK_INT>=29)startForeground(ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)else startForeground(ID,n)
        if(!NativeRpc.ready||(sourceMode=="tv"&&DeviceStore.load(this)==null)||(sourceMode=="mobile"&&!NuvioMobileProvider.allowed(this))||(sourceMode=="auto"&&preferences.getString("hz.studio","").isNullOrBlank()&&!NuvioMobileProvider.allowed(this)&&DeviceStore.load(this)==null)){Monitor.update{it.copy(running=false,connection="Autorize o acesso às notificações para Mobile ou pareie a TV",discord=NativeRpc.error.ifBlank{"Aguardando"})};stopSelf();return START_NOT_STICKY}
        sessionStarted=true
        Monitor.update{it.copy(running=true,studio=null,connection="Conectando",discord="SDK inicializado")};Monitor.log("Monitoramento iniciado")
        eclipseNotifications.start()
        callbackJob=scope.launch{while(isActive){NativeRpc.nativeTick();checkCallback();tickClear();delay(100)}}
        preferences.edit().putString("echo.resume",action).apply()
        val session=EchoSession(this)
        if(sourceMode!="mobile"){echo=session;session.open()}
        EchoHealth.failures.value=0
        val mobileObserver=if(sourceMode=="tv")null else MobileSessionObserver(this)
        polling=scope.launch{try{while(isActive){
            try {
                val options=Settings.options(this@PresenceService)
                val activePrivacy=privacy
                val readArt=options.artwork && activePrivacy.hidden.none {
                    it == Hidden.Disabled || it == Hidden.Artwork || it == Hidden.Title || it == Hidden.Spoilers
                }
                val local=if(sourceMode!="tv") withContext(Dispatchers.IO){mobileObserver?.read(readArt,activePrivacy)} else null
                val policy=preferences.getString("echo.policy","Automatic")?:"Automatic"
                val manual=sourceMode=="auto" && policy=="Manual"
                val manualMobile=manual && preferences.getBoolean("echo.manualMobile",false)
                val needsTv=sourceMode=="tv" || (sourceMode=="auto" && !manualMobile &&
                    !(local?.state=="playing" && (policy in listOf("Mobile Priority","Automatic") && currentSource=="mobile")))
                val remote=if(needsTv)try{session.tv(sourceMode=="auto" && !manual,readArt,::decode)}
                    catch(e:CancellationException){throw e}catch(e:Exception){if((local==null && preferences.getString("hz.studio","").isNullOrBlank())||sourceMode=="tv"||manual)throw e else null} else null
                val side=when{
                    sourceMode=="mobile"||manualMobile->"mobile"
                    sourceMode=="tv"||manual->"tv"
                    else->chooser.pick(policy,local?.state,remote?.second?.state,SystemClock.elapsedRealtime())
                }
                currentSource=if(side=="mobile")"mobile" else "tv"
                val key=if(side=="tv")remote?.first.orEmpty() else side
                val sample = (if (side == "mobile") local else remote?.second)
                    ?.takeIf { integrations.accepts(it.appId) && !(it.state == "paused" && integrations.clearOnPause(it.appId)) }
                val originKey = key + ":" + sample?.appId.orEmpty()
                if(lastSource!=originKey){clear();lastSource=originKey}
                val now=SystemClock.elapsedRealtime()
                if(!manual && sample?.state!="playing" && autoStudio(now)){artworkEngine.cancel();Monitor.manualArtwork.clear();session.waitFor(maxOf(3000L,preferences.getInt("interval",2).coerceIn(1,5)*1000L));continue}
                releaseFallback()
                val enabled = sample != null && artworkAllowed(Settings.options(this@PresenceService), privacy, sample.title) &&
                    integrations.artwork(sample.appId)
                val remoteKey = sample?.remoteArtworkKey.orEmpty()
                val bitmapRequest: (suspend () -> ByteArray?)? =
                    if (side == "tv" && key.isNotEmpty() && remoteKey.isNotEmpty())
                        ({ session.artwork(key, remoteKey) }) else null
                val external = side == "mobile" && sample?.appId in Horizon.nuvio &&
                    preferences.getBoolean("mobilePosterSearch", false)
                val selected = Monitor.manualArtwork.apply(sample, side == "mobile", enabled)
                val p = artworkEngine.resolve(selected, originKey, enabled, external, bitmapRequest)
                val artStatus = artworkDiagnostic(p)
                val previous=Monitor.state.value
                val sourceLabel=sourceName(p,currentSource)
                val text=if(p==null)"$sourceLabel • sem reprodução" else "$sourceLabel • conectado"
                if(previous.connection!=text)Monitor.event(Event.READY)
                if(previous.playback?.identity!=p?.identity&&p!=null)Monitor.log("Conteúdo atualizado")
                Monitor.update{it.copy(connection=text,playback=p,artworkStatus=artStatus)}
                failureCount=0
                if(preferences.getBoolean("chron.enabled",false) && p!=null && p.valid && privacy.hidden.isEmpty() && !privacy.blocks(p.title)){
                    val ep=if(options.episode)p.episode else ""
                    val kind=when{p.appId !in Horizon.nuvio->"Mídia";ep.isNotBlank()||Regex("(?i)S[0-9]+[: ]?E[0-9]+").containsMatchIn(p.title)->"Episódio";p.mediaId.contains("movie",true)->"Filme";else->"Mídia"}
                    val device=if(side=="tv")preferences.getString("echo.name."+key,null)?:"TV "+key.takeLast(6) else android.os.Build.MODEL
                    record(HistoryMedia(p.title.take(256),ep.take(256),if (p.artworkPermitted) publicArtwork(p.artwork, p.appId) else "",sourceLabel,device,kind),p.state=="playing" && p.speed>0,now)
                }else endHistory()
                reconcile(p,now,options)
                val configured=Settings.prefs(this@PresenceService).getInt("interval",2).coerceIn(1,5)*1000L
                // Keep the configured cadence for playback and seeks; save work when idle.
                session.waitFor(when {
                    p==null -> maxOf(configured,5000L)
                    p.state=="paused" -> maxOf(configured,3000L)
                    else -> configured
                })
            }catch(e:CancellationException){throw e}catch(e:Exception){
                val msg=when(e.message){"Sem rede"->"Rede indisponível • reconecte o Wi-Fi";"Protocolo incompatível"->"Protocolo incompatível • confira o Collector";"Acesso às notificações desativado"->"Autorize o acesso às notificações";"Pareamento revogado"->"Pareamento revogado na TV";"Coletor sem dados recentes"->"Coletor sem dados recentes";else->"Conexão indisponível • tentando novamente"}
                if(Monitor.state.value.connection!=msg)Monitor.log(msg)
                releaseFallback();endHistory();artworkEngine.cancel();Monitor.manualArtwork.clear();Monitor.update{it.copy(connection=msg,playback=null,artworkStatus="")};clear()
                failureCount=(failureCount+1).coerceAtMost(4)
                val retryDelay=retryDelay(failureCount,kotlin.random.Random.nextLong(1000))
                // Auto must continue checking the local source even while the TV is offline.
                session.waitFor(if(sourceMode=="tv")retryDelay else retryDelay.coerceAtMost(5000L))
            }
        }}finally{withContext(NonCancellable+Dispatchers.IO){mobileObserver?.close()}}}
        return START_STICKY
    }
    private fun startStudio(p:RpcPreset){
        sourceMode="studio"
        sessionStarted=true
        Monitor.update{it.copy(running=true,studio=p,playback=null,connection="RPC Studio",discord="Aguardando Discord")}
        Monitor.log("RPC Studio iniciado")
        eclipseNotifications.start()
        var activeSince=0L;var pendingSince=0L;var retryAt=0L;var refreshedAt=0L
        var checkpoint=-5000L
        var match=p.appPackage.isEmpty();var detectionAt=0L
        polling=scope.launch {
            try {
                while(isActive){
                    NativeRpc.nativeTick()
                    tickClear()
                    val now=SystemClock.elapsedRealtime()
                    if(p.appPackage.isNotEmpty() && now>=detectionAt){
                        match=withContext(Dispatchers.IO){runCatching{RpcAppDetection.matches(this@PresenceService,p.appPackage)}.getOrDefault(false)}
                        detectionAt=now+2000L
                    }
                    if(!match || privacy.hidden.isNotEmpty() || privacy.blocks(p.name)){
                        if(studioPending||studioPublished)requestClear()
                        studioPending=false;studioPublished=false;activeSince=0;retryAt=0
                        Monitor.update{it.copy(connection="RPC Studio • aguardando aplicativo",discord="Presença inativa",artworkForwarded=false)}
                    }else{
                        if(activeSince==0L)activeSince=System.currentTimeMillis()
                        val end=if(p.timer=="countdown")activeSince+p.minutes*60000L else 0L
                        if(end>0&&System.currentTimeMillis()>=end){stopMonitoring();break}
                        if(studioPending){
                            val result=NativeRpc.nativeOutcome()
                            if(result==1){studioPending=false;studioPublished=true;refreshedAt=now;Monitor.update{it.copy(discord="Presença confirmada")}}
                            else if(result==-1||now-pendingSince>12000L){studioPending=false;studioPublished=false;requestClear();retryAt=now+6000L;Monitor.update{it.copy(discord="Discord não confirmou • nova tentativa")}}
                        }
                        if(!studioPending&&(!studioPublished||now-refreshedAt>=60000L)&&now>=retryAt){
                            clearRetries.cancel()
                            val forwarded = NativeRpc.publishStudio(p,if(p.timer=="none")0 else activeSince,end)
                            studioPending=true;pendingSince=now
                            Monitor.update{it.copy(connection="RPC Studio • ativo",discord="Atualizando presença",artworkForwarded=forwarded)}
                        }
                    }
                    if(match && studioPublished && preferences.getBoolean("chron.enabled",false) && privacy.hidden.isEmpty() && !privacy.blocks(p.name)){
                        if(now-checkpoint>=5000 || !recording){checkpoint=now;record(HistoryMedia(p.name,"",historyCover(p.largeImage),"RPC Studio",android.os.Build.MODEL,if(p.type=="playing")"Jogo" else "Custom RPC"),true,now)}
                    }else endHistory()
                    delay(100)
                }
            }catch(e:CancellationException){throw e}catch(e:Exception){Monitor.log("Falha no RPC Studio");stopMonitoring()}
        }
    }
    private fun clearStudio(){if(studioPending||studioPublished){requestClear();studioPending=false;studioPublished=false}}
    private fun decode(j:JSONObject,now:Long,showArtwork:Boolean):Playback?{
        if(!j.optBoolean("active"))return null
        val age=j.optLong("sample_age_ms",0).coerceIn(0,6000)
        val state=j.optString("state","none");val speed=j.optDouble("speed",0.0).let{if(it.isFinite())it else 0.0}
        val position=j.optLong("position_ms",-1);val duration=j.optLong("duration_ms",-1)
        var pos=position
        if(pos>=0&&state=="playing"&&speed>0)pos+=(age*speed).toLong()
        if(duration>0&&pos>=0)pos=pos.coerceAtMost(duration)
        val app = j.optString("package", "com.nuvio.tv")
        val id = j.optString("media_id")
        val title = j.optString("title")
        val raw = j.optString("artwork_url")
        val playback = Playback(title, j.optString("artist"), id, "", state, pos, duration, speed, now,
            j.optInt("year").takeIf { it in 1000..9999 }?.toString().orEmpty(), j.optString("genre"), app)
        if (!showArtwork || privacy.blocks(title) || !integrations.artwork(app)) return playback.withoutArtwork()
        val direct = publicArtwork(raw, app)
        val identifier = if (direct.isBlank()) ArtworkPolicy.fromMediaId(id, app) else ""
        val capabilities = j.optJSONArray("capabilities")
        val supportsBitmap = capabilities != null && (0 until capabilities.length())
            .any { capabilities.optString(it) == "artwork_bitmap" }
        val key = j.optString("artwork_key").takeIf { supportsBitmap && ArtworkPolicy.validLocalKey(it) }.orEmpty()
        val source = when {
            direct.isNotEmpty() -> j.optString("artwork_source").takeIf { it in setOf("session", "identifier", "metadata", "media_uri") } ?: "session"
            identifier.isNotEmpty() -> "identifier"
            else -> ""
        }
        return playback.copy(artwork = direct.ifBlank { identifier }, artworkSource = source,
            artworkRejected = j.optBoolean("artwork_rejected") || raw.isNotEmpty() && direct.isEmpty(),
            remoteArtworkKey = key)
    }

    private fun artworkDiagnostic(p: Playback?): String {
        if (p == null) return ""
        if (!p.artworkPermitted) return "Desativada"
        val source = when (p.artworkSource) {
            "session" -> "URL da MediaSession"
            "metadata" -> "URL dos metadados"
            "media_uri" -> "Capa derivada da URI de mídia"
            "manual" -> "Capa informada nesta sessão"
            "identifier" -> "URL por identificador público"
            "api" -> "Catálogo Cinemeta"
            "cache" -> "Cache de resolução"
            "pending" -> "Resolvendo capa"
            "miss" -> "Falha na resolução"
            else -> if (p.localArtwork == null) "Capa indisponível" else ""
        }
        return listOf(source, if (p.artworkRejected) "URL rejeitada" else "",
            if (p.localArtwork != null) "Bitmap disponível só no aparelho"
            else if (p.remoteArtworkKey.isNotEmpty() && p.artwork.isEmpty()) "Bitmap anunciado pelo Collector"
            else "").filter(String::isNotEmpty).joinToString(" · ")
    }

    private fun checkCallback(){
        val p=pending?:return
        when(NativeRpc.nativeOutcome()){
            1->{published=p;pending=null;Monitor.update{it.copy(discord="Presença confirmada")};Monitor.log("Discord confirmou a presença")}
            -1->{pending=null;published=null;requestClear();retryAt=SystemClock.elapsedRealtime()+6000;Monitor.update{it.copy(discord="Discord recusou a atualização • nova tentativa")};Monitor.log("Falha no callback do Discord")}
            else->if(SystemClock.elapsedRealtime()-pendingAt>12000){pending=null;published=null;requestClear();retryAt=SystemClock.elapsedRealtime()+6000;Monitor.update{it.copy(discord="Discord não respondeu • nova tentativa")};Monitor.log("Timeout do Discord")}
        }
    }
    private fun reconcile(p:Playback?,now:Long,options:Options){
        reconcilePresence(mediaPresence(p,options,format,privacy,sourceName(p,currentSource),now,System.currentTimeMillis()),now)
    }
    private fun reconcilePresence(next:Presence?,now:Long){
        if(next==null){clear();return}
        val old=pending?:published
        // Immediately clear obsolete timers when changing media or leaving normal playback.
        if(old!=null&&(old.identity!=next.identity||old.start>0&&next.start==0L))clear()
        val current=pending?:published
        if(!needsUpdate(current,next))return
        if(pending!=null)return
        if(now<retryAt||now-attemptAt<2500)return
        clearRetries.cancel();attemptAt=now;pendingAt=now;pending=next
        val forwarded = NativeRpc.publish(next)
        Monitor.update { it.copy(discord = "Atualizando presença", artworkForwarded = forwarded) }
    }
    private fun clear(){
        if(pending!=null||published!=null){requestClear();pending=null;published=null;Monitor.update{it.copy(discord="Presença removida (solicitado)", artworkForwarded = false)};Monitor.log("Limpeza solicitada ao Discord")}
    }
    private fun stopMonitoring(){
        if(stopping)return
        preferences.edit().remove("echo.resume").apply();Monitor.event(Event.STOP)
        endHistory(true)
        stopping=true
        Monitor.manualArtwork.clear()
        artworkEngine.cancel(clearCache = true)
        polling?.cancel()
        callbackJob?.cancel()
        clearRetries.cancel()
        pending=null;published=null;studioPending=false;studioPublished=false
        Monitor.update{it.copy(running=true,connection="Encerrando",discord="Solicitando limpeza ao Discord",playback=null,studio=null,artworkStatus="",artworkForwarded=false)}
        eclipseNotifications.start()
        scope.launch{
            try{
                if(NativeRpc.ready){
                    Monitor.log("Limpeza RPC solicitada; processando callbacks e tentativas limitadas")
                    drainRichPresence(NativeRpc::nativeClear,NativeRpc::nativeTick,{delay(it)})
                    cleanupFinished=true
                    Monitor.update{it.copy(discord="Limpeza enviada • confira seu perfil")}
                    Monitor.log("Limpeza enviada; SDK não confirma remoção remota")
                }else{
                    Monitor.update{it.copy(discord="SDK indisponível • tente limpar novamente")}
                }
            }catch(e:CancellationException){throw e}catch(e:Exception){
                Monitor.update{it.copy(discord="Falha ao solicitar limpeza • tente novamente")}
                Monitor.log("Falha ao solicitar limpeza RPC")
            }finally{
                eclipseNotifications.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }
    override fun onDestroy(){
        endHistory()
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        eclipseNotifications.stop();artworkEngine.cancel(clearCache = true);Monitor.manualArtwork.clear();polling?.cancel();callbackJob?.cancel();scope.cancel()
        if(NativeRpc.ready&&!cleanupFinished&&(sessionStarted||stopping)){
            // Android can kill a process without delivering onDestroy. Best effort only.
            runCatching{NativeRpc.nativeClear();NativeRpc.nativeTick()}
        }
        Monitor.update{it.copy(running=false,connection="Desconectado",playback=null,studio=null,artworkStatus="",artworkForwarded=false)}
        echo?.close();super.onDestroy()
    }
}



