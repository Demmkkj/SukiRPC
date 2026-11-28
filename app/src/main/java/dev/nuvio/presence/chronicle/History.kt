package dev.nuvio.presence

import android.content.*
import android.database.sqlite.*
import android.os.SystemClock
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

internal data class HistoryRow(val id:Long,val media:HistoryMedia,val start:Long,val end:Long,val watched:Long,val connected:Long)
internal class History private constructor(val ctx:Context):SQLiteOpenHelper(ctx,"chronicle.db",null,1){
    companion object{
        @Volatile private var instance:History?=null
        fun get(c:Context)=instance?:synchronized(this){instance?:History(c.applicationContext).also{instance=it}}
    }
    private val io=Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    val revision=MutableStateFlow(0L)
    private var id=0L
    private var identity:HistoryMedia?=null
    private var previous=0L
    private var playing=false
    private var lastPrune=0L
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY,name TEXT,episode TEXT,cover TEXT,source TEXT,device TEXT,category TEXT,start INTEGER,end INTEGER,watched INTEGER DEFAULT 0,connected INTEGER DEFAULT 0,resumable INTEGER DEFAULT 1)")
        db.execSQL("CREATE INDEX history_end ON history(end)")
    }
    override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int){}
    private fun prune(now:Long){
        val days=Settings.prefs(ctx).getInt("chron.days",30)
        if(days>0)writableDatabase.delete("history","end < ?",arrayOf((now-days*86400000L).toString()))
        lastPrune=now
    }
    private val scope=CoroutineScope(SupervisorJob()+io)
    fun maintain(){scope.launch{runCatching{prune(System.currentTimeMillis());revision.value++}}}
    fun boundary(final:Boolean){
        scope.launch{if(final && id!=0L)runCatching{writableDatabase.execSQL("UPDATE history SET resumable=0 WHERE id=?",arrayOf(id))}
            id=0;identity=null;previous=0;playing=false}
    }
    fun sample(m:HistoryMedia,active:Boolean,mono:Long=SystemClock.elapsedRealtime()){
        val wall=System.currentTimeMillis()
        scope.launch{runCatching{
            val prefs=Settings.prefs(ctx);val privacy=readPrivacy(prefs)
            if(!prefs.getBoolean("chron.enabled",false)||privacy.hidden.isNotEmpty()||privacy.blocks(m.name))return@launch
            if(wall-lastPrune>3600000L)prune(wall)
            val db=writableDatabase
            val same=identity?.copy(cover=m.cover)==m && id!=0L
            if(!same){
                id=0
                db.rawQuery("SELECT id,name,episode,source,device,category,end,resumable FROM history ORDER BY id DESC LIMIT 1",null).use{c->
                    if(c.moveToFirst() && canResume(c.getInt(7)==1 && listOf(1,2,3,4,5).map(c::getString)==listOf(m.name,m.episode,m.source,m.device,m.category),c.getLong(6),wall))id=c.getLong(0)
                }
                if(id==0L)id=db.insertOrThrow("history",null,ContentValues().apply{put("name",m.name);put("episode",m.episode);put("source",m.source);put("device",m.device);put("cover",m.cover);put("category",m.category);put("start",wall);put("end",wall)})
            }
            val elapsed=observedTime(previous,mono,same)
            db.execSQL("UPDATE history SET end=?,watched=watched+?,connected=connected+?,cover=? WHERE id=?",arrayOf(wall,if(playing)elapsed else 0L,elapsed,m.cover,id))
            identity=m;previous=mono;playing=active;revision.value++
        }}
    }
    suspend fun rows()=withContext(io){
        prune(System.currentTimeMillis())
        writableDatabase.rawQuery("SELECT id,name,episode,cover,source,device,category,start,end,watched,connected FROM history ORDER BY start DESC",null).use{c->buildList{
            while(c.moveToNext())add(HistoryRow(c.getLong(0),HistoryMedia(c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6)),c.getLong(7),c.getLong(8),c.getLong(9),c.getLong(10)))
        }}
    }
    suspend fun remove(row:Long?)=withContext(io){
        writableDatabase.delete("history",if(row==null)null else "id=?",row?.let{arrayOf(it.toString())})
        if(row==null||row==id){id=0;identity=null};revision.value++
    }
}
