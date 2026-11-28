package dev.nuvio.presence

internal data class HistoryMedia(val name:String,val episode:String,val cover:String,val source:String,val device:String,val category:String)
internal fun observedTime(previous:Long,now:Long,wasActive:Boolean):Long =
    (now-previous).takeIf{wasActive && it in 1..10000}?:0
internal fun canResume(same:Boolean,last:Long,now:Long)=same && now-last in 0..30000
internal fun csvCell(value:String):String {
    val safe=if(value.trimStart().firstOrNull() in listOf('=','+','-','@')) "'"+value else value
    return "\""+safe.replace("\"","\"\"")+"\""
}
internal fun historyCover(raw:String):String=runCatching{
    val u=java.net.URI(raw)
    if(u.scheme=="https" && u.rawUserInfo==null && u.rawQuery==null && u.rawFragment==null && u.port==-1 &&
        u.host in setOf("cdn.discordapp.com","media.discordapp.net","image.tmdb.org","images.metahub.space","media.tenor.com","i.giphy.com") &&
        u.path.length<=400)raw else ""
}.getOrDefault("")

/** Classify old rows using their saved category and source, without rewriting the database. */
internal fun chronicleCategory(media:HistoryMedia):String {
    val source=media.source.lowercase(java.util.Locale.ROOT)
    return when {
        media.category=="Jogo" -> "Jogos"
        media.category=="Custom RPC" || source=="rpc studio" -> "Custom RPC"
        media.category=="Música" || source.startsWith("spotify ") || source=="spotify" ||
            source.startsWith("youtube music") -> "Músicas"
        media.category in setOf("Filme","Episódio","Série") -> "Séries e filmes"
        media.category=="Vídeo" || source.startsWith("youtube ") || source=="youtube" -> "Vídeos"
        else -> "Outros"
    }
}
