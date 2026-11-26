package dev.nuvio.presence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class Dashboard(val running:Boolean=false,val connection:String="Desconectado",val discord:String="Aguardando",val playback:Playback?=null,val logs:List<String> = emptyList())
internal object Monitor {
    private val mutable=MutableStateFlow(Dashboard())
    val state=mutable.asStateFlow()
    fun update(block:(Dashboard)->Dashboard){mutable.update(block)}
    fun log(text:String){update{it.copy(logs=(listOf(java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.ROOT).format(java.util.Date())+"  "+text)+it.logs).take(80))}}
}
