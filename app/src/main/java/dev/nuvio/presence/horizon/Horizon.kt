package dev.nuvio.presence

internal enum class Origin { Mobile, TV, Studio }
internal data class SourceId(val origin:Origin,val app:String)
internal data class ProviderSpec(val id:SourceId,val label:String,val native:Boolean,val capabilities:Set<String>)
internal interface ActivityProvider { fun describe(id:SourceId):ProviderSpec? }
internal object Horizon:ActivityProvider {
    val nuvio=setOf("com.nuvio.app","com.nuviodebug.com","com.nuvio.tv")
    val catalog=mapOf("com.stremio.one" to "Stremio","org.jellyfin.mobile" to "Jellyfin","org.jellyfin.androidtv" to "Jellyfin TV",
        "com.plexapp.android" to "Plex","org.xbmc.kodi" to "Kodi","org.videolan.vlc" to "VLC","is.xyz.mpv" to "mpv",
        "com.spotify.music" to "Spotify","com.spotify.tv.android" to "Spotify TV","com.google.android.youtube" to "YouTube",
        "com.google.android.youtube.tv" to "YouTube TV","com.google.android.apps.youtube.music" to "YouTube Music")
    override fun describe(id:SourceId):ProviderSpec? {
        if(id.origin==Origin.Studio)return ProviderSpec(id,"RPC Studio",true,setOf("custom","game","artwork"))
        if(!id.app.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) || id.app.length>160)return null
        val label=if(id.app in nuvio)"Nuvio" else catalog[id.app]?:id.app
        return ProviderSpec(id,"$label ${id.origin.name}",id.app in nuvio,setOf("MediaSession"))
    }
    fun allowed(pkg:String,enabled:Set<String>,blocked:Set<String>)=pkg !in blocked && (pkg in nuvio || pkg in enabled)
    fun <T> choose(items:List<T>,app:(T)->String,playing:(T)->Boolean,priority:(String)->Int,previous:String?):T?=
        items.sortedWith(compareByDescending<T>{playing(it)}.thenByDescending{priority(app(it))}.thenByDescending{app(it)==previous}.thenBy{app(it)}).firstOrNull()
}
