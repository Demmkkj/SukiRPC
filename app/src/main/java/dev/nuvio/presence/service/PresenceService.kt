package dev.nuvio.presence

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import kotlinx.coroutines.*
import org.json.JSONObject


class PresenceService:Service(){
    companion object {const val STOP="dev.nuvio.presence.STOP";const val START="dev.nuvio.presence.START";const val START_MOBILE="dev.nuvio.presence.START_MOBILE";const val START_AUTO="dev.nuvio.presence.START_AUTO";private const val CHANNEL="presence";private const val ID=33}
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var polling:Job?=null
    private var callbackJob:Job?=null
    private var published:Presence?=null
    private var pending:Presence?=null
    private var pendingAt=0L
    private var attemptAt=-100000L
    private var retryAt=0L
    private var stopping=false
    private var sourceMode="tv"
    private var currentSource="tv"
    private var lastArtworkStatus=""
    private var failureCount=0
    override fun onBind(intent:Intent?)=null
    override fun onCreate(){super.onCreate();val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel(CHANNEL,"Sincronização SukiRPC",NotificationManager.IMPORTANCE_LOW))}
    private val eclipseNotifications by lazy {
        dev.nuvio.presence.ui.eclipse.EclipseNotifications(this,scope,CHANNEL,ID,STOP)
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==STOP){stopMonitoring();return START_NOT_STICKY}
        if(polling?.isActive==true||stopping)return START_NOT_STICKY
        sourceMode=when(intent?.action){START_MOBILE->"mobile";START_AUTO->"auto";else->"tv"}
        currentSource=sourceMode
        val n=eclipseNotifications.starting()
        if(Build.VERSION.SDK_INT>=29)startForeground(ID,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)else startForeground(ID,n)
        if(!NativeRpc.ready||(sourceMode=="tv"&&DeviceStore.load(this)==null)||(sourceMode=="mobile"&&!NuvioMobileProvider.allowed(this))||(sourceMode=="auto"&&!NuvioMobileProvider.allowed(this)&&DeviceStore.load(this)==null)){Monitor.update{it.copy(running=false,connection="Autorize o acesso às notificações para Mobile ou pareie a TV",discord=NativeRpc.error.ifBlank{"Aguardando"})};stopSelf();return START_NOT_STICKY}
        Monitor.update{it.copy(running=true,connection="Conectando",discord="SDK inicializado")};Monitor.log("Monitoramento iniciado")
        eclipseNotifications.start()
        callbackJob=scope.launch{while(isActive){NativeRpc.nativeTick();checkCallback();delay(100)}}
        val mobileObserver=if(sourceMode=="tv")null else MobileSessionObserver(this)
        polling=scope.launch{try{while(isActive){
            try {
                val options=Settings.options(this@PresenceService)
                val local=if(sourceMode!="tv") withContext(Dispatchers.IO){mobileObserver?.read(options.artwork)} else null
                val device=if(sourceMode=="tv"||(sourceMode=="auto"&&local==null)) withContext(Dispatchers.IO){DeviceStore.load(this@PresenceService)} else null
                val useMobile=sourceMode=="mobile"||(sourceMode=="auto"&&(local!=null||device==null))
                val p=if(useMobile){
                    if(!NuvioMobileProvider.allowed(this@PresenceService))error("Acesso às notificações desativado")
                    currentSource="mobile";local
                }else{
                    currentSource="tv"
                    val result=withContext(Dispatchers.IO){LanClient.playback(device?:error("Sem pareamento"))}
                    if(result.has("error"))error("MediaSession indisponível")
                    decode(result,SystemClock.elapsedRealtime(),options.artwork)
                }
                val now=SystemClock.elapsedRealtime()
                val artStatus=when {
                    p==null -> ""
                    !options.artwork -> "Capas: desativadas nos ajustes"
                    currentSource=="mobile" -> "Capas (mobile): "+NuvioMobileProvider.artworkStatus
                    p.artwork.isNotBlank() -> "Capas ($currentSource): URL publica aceita"
                    else -> "Capas (tv): nenhuma URL publica aceita"
                }
                if(artStatus!=lastArtworkStatus){lastArtworkStatus=artStatus;if(artStatus.isNotBlank())Monitor.log(artStatus)}
                val previous=Monitor.state.value
                val sourceLabel=if(currentSource=="mobile")"Nuvio Mobile" else "Nuvio TV"
                val text=if(p==null)"$sourceLabel • sem reprodução" else "$sourceLabel • conectado"
                if(previous.connection!=text)Monitor.log(text)
                if(previous.playback?.identity!=p?.identity&&p!=null)Monitor.log("Conteúdo atualizado")
                Monitor.update{it.copy(connection=text,playback=p)}
                failureCount=0
                reconcile(p,now,options)
                val configured=Settings.prefs(this@PresenceService).getInt("interval",2).coerceIn(1,5)*1000L
                // Keep the configured cadence for playback and seeks; save work when idle.
                delay(when {
                    p==null -> maxOf(configured,5000L)
                    p.state=="paused" -> maxOf(configured,3000L)
                    else -> configured
                })
            }catch(e:CancellationException){throw e}catch(e:Exception){
                val msg=when(e.message){"Acesso às notificações desativado"->"Autorize o acesso às notificações";"Pareamento revogado"->"Pareamento revogado na TV";"Coletor sem dados recentes"->"Coletor sem dados recentes";else->"Conexão indisponível • tentando novamente"}
                if(Monitor.state.value.connection!=msg)Monitor.log(msg)
                Monitor.update{it.copy(connection=msg,playback=null)};clear()
                failureCount=(failureCount+1).coerceAtMost(4)
                val retryDelay=when(failureCount){1->2000L;2->4000L;3->8000L;else->15000L}
                // Auto must continue checking the local source even while the TV is offline.
                delay(if(sourceMode=="tv")retryDelay else retryDelay.coerceAtMost(5000L))
            }
        }}finally{withContext(NonCancellable+Dispatchers.IO){mobileObserver?.close()}}}
        return START_NOT_STICKY
    }
    private fun decode(j:JSONObject,now:Long,showArtwork:Boolean):Playback?{
        if(!j.optBoolean("active"))return null
        val age=j.optLong("sample_age_ms",0).coerceIn(0,6000)
        val state=j.optString("state","none");val speed=j.optDouble("speed",0.0).let{if(it.isFinite())it else 0.0}
        val position=j.optLong("position_ms",-1);val duration=j.optLong("duration_ms",-1)
        var pos=position
        if(pos>=0&&state=="playing"&&speed>0)pos+=(age*speed).toLong()
        if(duration>0&&pos>=0)pos=pos.coerceAtMost(duration)
        return Playback(j.optString("title"),j.optString("artist"),j.optString("media_id"),(if(showArtwork)publicArtwork(j.optString("artwork_url"))else ""),state,pos,duration,speed,now)
    }
    private fun checkCallback(){
        val p=pending?:return
        when(NativeRpc.nativeOutcome()){
            1->{published=p;pending=null;Monitor.update{it.copy(discord="Presença confirmada")};Monitor.log("Discord confirmou a presença")}
            -1->{pending=null;published=null;NativeRpc.nativeClear();retryAt=SystemClock.elapsedRealtime()+6000;Monitor.update{it.copy(discord="Discord recusou a atualização • nova tentativa")};Monitor.log("Falha no callback do Discord")}
            else->if(SystemClock.elapsedRealtime()-pendingAt>12000){pending=null;published=null;NativeRpc.nativeClear();retryAt=SystemClock.elapsedRealtime()+6000;Monitor.update{it.copy(discord="Discord não respondeu • nova tentativa")};Monitor.log("Timeout do Discord")}
        }
    }
    private fun reconcile(p:Playback?,now:Long,options:Options){
        reconcilePresence(presenceFor(p,options,now,System.currentTimeMillis())?.copy(activityName=if(currentSource=="mobile")"Nuvio Mobile" else "Nuvio TV"),now)
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
        attemptAt=now;pendingAt=now;pending=next;NativeRpc.publish(next);Monitor.update{it.copy(discord="Atualizando presença")}
    }
    private fun clear(){
        if(pending!=null||published!=null){NativeRpc.nativeClear();pending=null;published=null;Monitor.update{it.copy(discord="Presença removida (solicitado)")};Monitor.log("Limpeza solicitada ao Discord")}
    }
    private fun stopMonitoring(){
        if(stopping)return;stopping=true;eclipseNotifications.stop();polling?.cancel();clear();Monitor.update{it.copy(running=false,connection="Desconectado",playback=null)}
        scope.launch{delay(500);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    }
    override fun onDestroy(){eclipseNotifications.stop();polling?.cancel();if(NativeRpc.ready){clear();NativeRpc.nativeTick()};scope.cancel();Monitor.update{it.copy(running=false,connection="Desconectado",playback=null)};super.onDestroy()}
}
