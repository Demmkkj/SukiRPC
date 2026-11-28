package dev.nuvio.presence

import android.app.*
import android.appwidget.*
import android.content.*
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AuroraApplication:Application(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val settingsRevision=MutableStateFlow(0)
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
        if(key?.startsWith("prism.")==true || key?.startsWith("hz.art.")==true || key in setOf("art","time","episode","watch","clearPaused"))settingsRevision.value++
    }
    override fun onCreate(){
        super.onCreate()
        Settings.prefs(this).registerOnSharedPreferenceChangeListener(listener)
        scope.launch{
            combine(Monitor.state.map{it.running}.distinctUntilChanged(),settingsRevision){_,_->Unit}.collect{
                for(type in listOf(PresenceTile::class.java,PrivacyTile::class.java))TileService.requestListeningState(this@AuroraApplication,ComponentName(this@AuroraApplication,type))
            }
        }
        scope.launch{combine(Monitor.state,settingsRevision){d,_->d}.collect{HomeWidgets.refresh(this@AuroraApplication,it)}}
    }
}
internal object HomeWidgets {
    private val types=listOf(CompactWidget::class.java,WatchingWidget::class.java,DeviceWidget::class.java)
    private var installed:List<IntArray>?=null
    private var previous:WidgetSnapshot?=null
    private var coverJob:Job?=null
    private var bitmap:Bitmap?=null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    fun refresh(c:Context,d:Dashboard=Monitor.state.value,force:Boolean=false){
        val manager=AppWidgetManager.getInstance(c)
        val ids=installed?.takeUnless{force}?:types.map{manager.getAppWidgetIds(ComponentName(c,it))}.also{installed=it}
        if(ids.all{it.isEmpty()}){previous=null;bitmap=null;coverJob?.cancel();return}
        val prefs=Settings.prefs(c)
        val state=widgetSnapshot(d.running,d.connection,d.discord,d.playback,Settings.options(c),readFormat(prefs.all),readPrivacy(prefs),SystemClock.elapsedRealtime())
        val old=previous
        if(!force && old==state)return
        previous=state
        val artChanged = old?.cover != state.cover || old?.localCover !== state.localCover
        if (artChanged || ids[1].isEmpty()) { coverJob?.cancel(); bitmap = null }
        ids.forEachIndexed { index,group -> group.forEach { id ->
            val compact=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)<150
            val v=RemoteViews(c.packageName,R.layout.suki_widget)
            v.setTextViewText(R.id.widget_title,when(index){0->"SukiRPC";1->state.title.ifBlank{"Now Watching"};else->"Dispositivos"})
            v.setTextViewText(R.id.widget_detail,when(index){0->state.status+" · "+state.device;1->state.details;else->state.device})
            val image=if(index==1 && !compact)bitmap else null
            v.setImageViewBitmap(R.id.widget_image,image);v.setViewVisibility(R.id.widget_image,if(image==null)View.GONE else View.VISIBLE)
            v.setOnClickPendingIntent(R.id.widget_root,shortcut(c,when(index){0->"toggle";2->"devices";else->"home"}))
            manager.updateAppWidget(id,v)
        }}
        if ((state.cover.isNotEmpty() || state.localCover != null) && ids[1].isNotEmpty() &&
            (artChanged || (force && bitmap == null && coverJob?.isActive != true))) {
            coverJob = scope.launch {
                val image = ArtworkImages.bitmap(c, state.cover, state.localCover, 128, 128)
                val active = previous
                if (image != null && active != null && active.cover == state.cover && active.localCover === state.localCover) {
                    bitmap = image
                    refresh(c, force = true)
                }
            }
        }
    }
}
internal fun shortcut(c:Context,action:String)=PendingIntent.getActivity(c,action.hashCode(),
    Intent(c,ControlActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
class ControlActivity:MainActivity()
open class SukiWidget:AppWidgetProvider(){
    override fun onDisabled(c:Context)=HomeWidgets.refresh(c,force=true)
    override fun onDeleted(c:Context,ids:IntArray)=HomeWidgets.refresh(c,force=true)
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray)=HomeWidgets.refresh(c,force=true)
    override fun onAppWidgetOptionsChanged(c:Context,m:AppWidgetManager,id:Int,options:android.os.Bundle)=HomeWidgets.refresh(c,force=true)
}
class CompactWidget:SukiWidget()
class WatchingWidget:SukiWidget()
class DeviceWidget:SukiWidget()
open class PresenceTile:TileService(){
    protected open val privacyControl=false
    override fun onStartListening(){super.onStartListening()
        val active=if(privacyControl)Hidden.Disabled in readPrivacy(Settings.prefs(this)).hidden else Monitor.state.value.running
        qsTile?.apply{state=if(active)Tile.STATE_ACTIVE else Tile.STATE_INACTIVE;updateTile()}
    }
    override fun onClick(){super.onClick()
        unlockAndRun{
            if(privacyControl){val prefs=Settings.prefs(this);val p=readPrivacy(prefs)
                privacyAfterTap(p).save(prefs)
                onStartListening()
            }else if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(shortcut(this,"toggle"))
            else @Suppress("DEPRECATION") startActivityAndCollapse(Intent(this,ControlActivity::class.java).setAction("toggle").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
class PrivacyTile:PresenceTile(){override val privacyControl=true}
