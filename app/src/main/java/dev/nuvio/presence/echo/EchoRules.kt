package dev.nuvio.presence

internal const val ECHO_TYPE="_sukirpc._tcp."
internal fun protocolOK(version:Int,age:Long)=version in 1..2 && age in 0..6000
internal fun retryDelay(failures:Int,jitter:Long)=((2000L shl (failures-1).coerceIn(0,4))+jitter.coerceIn(0,999))
internal fun validHint(pin:String,port:Int,ipv4:Boolean)=ipv4 && port==8766 && pin.matches(Regex("[0-9a-f]{64}"))
internal class SourceChoice {
    private var active=""
    private var candidate=""
    private var since=0L
    fun pick(mode:String,mobile:String?,tv:String?,now:Long):String{
        val preferred=when {
            mobile==null -> if(tv==null)"" else "tv"
            tv==null -> "mobile"
            (mobile=="playing")!=(tv=="playing") -> if(mobile=="playing")"mobile" else "tv"
            mode=="TV Priority" -> "tv"
            mode=="Mobile Priority" -> "mobile"
            active.isNotBlank() -> active
            else -> "mobile"
        }
        if(preferred!=candidate){candidate=preferred;since=now}
        if(active.isBlank() || active=="mobile"&&mobile==null || active=="tv"&&tv==null || now-since>=1200)active=preferred
        return active
    }
}
internal enum class Event(val level:String,val area:String,val label:String){
    START("Info","Sistema","Monitoramento iniciado"), STOP("Info","Sistema","Monitoramento encerrado"),
    PAIRED("Info","Collector","Pareamento salvo"), LOST("Aviso","Conexão","Conexão indisponível"),
    AUTH("Erro","Collector","Credencial ou identidade recusada"), FORMAT("Erro","Collector","Resposta incompatível"),
    READY("Info","Conexão","Fonte conectada"), MEDIA("Info","Reprodução","Conteúdo atualizado"),
    PUBLISHED("Info","Discord","Discord confirmou a presença"), CLEAR("Info","Discord","Limpeza solicitada"),
    DISCORD("Erro","Discord","Atualização não confirmada"), SAMPLE("Info","Collector","Amostra autenticada")
}
internal fun safeEvent(raw:String):Event?=when(raw){
    "Monitoramento iniciado","RPC Studio iniciado"->Event.START
    "Fire TV pareada","Collector instalado e autorizado"->Event.PAIRED
    "Conteúdo atualizado"->Event.MEDIA
    "Discord confirmou a presença"->Event.PUBLISHED
    "Limpeza solicitada ao Discord"->Event.CLEAR
    "Timeout do Discord","Falha no callback do Discord","Falha no RPC Studio"->Event.DISCORD
    else->null
}
internal fun report(lines:List<String>)=lines.take(80).filter { line ->
    Event.entries.any{line.matches(Regex("""\d{2}:\d{2}:\d{2} """+Regex.escape("${it.level} · ${it.area} · ${it.label}")))}
}.joinToString("\n")

internal class NetworkEpoch {
    private val generation=java.util.concurrent.atomic.AtomicInteger()
    fun changed(){generation.incrementAndGet()}
    fun current()=generation.get()
    fun accepts(previous:Int)=previous==current()
}
internal fun routes(selected:String,paired:List<String>,auto:Boolean)=
    if(auto) listOf(selected)+(paired-selected).distinct() else listOf(selected)
internal fun acceptsPin(pin:String,fingerprint:String)=pin.matches(Regex("[0-9a-f]{16}|[0-9a-f]{64}")) &&
    java.security.MessageDigest.isEqual(pin.toByteArray(),fingerprint.take(pin.length).toByteArray())

internal fun migratedPair(pin:String,encrypted:String)=mapOf("pair."+pin to encrypted,"active" to pin)
internal fun mayRelocate(oldPin:String,newPin:String,oldIp:String,savedIp:String?)=oldPin==newPin && savedIp==oldIp
