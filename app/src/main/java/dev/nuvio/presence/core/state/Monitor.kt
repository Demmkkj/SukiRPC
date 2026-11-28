package dev.nuvio.presence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class Dashboard(val running:Boolean=false,val connection:String="Desconectado",val discord:String="Aguardando",val playback:Playback?=null,val logs:List<String> = emptyList(),val studio:RpcPreset?=null,
    val artworkStatus: String = "", val artworkForwarded: Boolean = false)
internal object Monitor {
    val manualArtwork = SessionArtworkOverride()
    private val mutable=MutableStateFlow(Dashboard())
    val state=mutable.asStateFlow()
    fun update(block:(Dashboard)->Dashboard){mutable.update(block)}
    fun log(text:String){safeEvent(text)?.let(::event)}
    private val events=java.util.ArrayDeque<String>(80)
    @Synchronized fun event(e:Event){
        val time=java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(java.util.Date())
        if(events.size==80)events.removeLast()
        events.addFirst("$time ${e.level} · ${e.area} · ${e.label}")
        update{it.copy(logs=events.toList())}
    }
}
