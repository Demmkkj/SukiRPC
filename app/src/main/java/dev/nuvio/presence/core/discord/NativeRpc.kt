package dev.nuvio.presence

import android.app.Activity

internal object NativeRpc {
    var ready=false; private set
    var error="SDK não inicializado"; private set
    fun prepare(activity:Activity):Boolean {
        return try {
            System.loadLibrary("discord_partner_sdk")
            com.discord.socialsdk.DiscordSocialSdkInit.setEngineActivity(activity)
            System.loadLibrary("nuvio_rpc")
            nativeInit();ready=true;error="";true
        }catch(e:Throwable){error="SDK: ${e.javaClass.simpleName}";ready=false;false}
    }
    private external fun nativeInit()
    private external fun nativePublish(title:ByteArray,detail:ByteArray,art:ByteArray,watching:Boolean,start:Long,end:Long,activityName:ByteArray)
    private external fun nativeStudio(fields:Array<ByteArray>,type:Int,start:Long,end:Long)
    fun publishStudio(p:RpcPreset,start:Long,end:Long): Boolean {
        val fields=listOf(p.name,p.details,p.state,studioAsset(p.largeImage),p.largeText,studioAsset(p.smallImage),p.smallText,p.button1,p.url1,p.button2,p.url2)
        nativeStudio(fields.map{it.toByteArray(Charsets.UTF_8)}.toTypedArray(),rpcTypes.keys.indexOf(p.type),start,end)
        return fields[3].isNotEmpty() || fields[5].isNotEmpty()
    }
    external fun nativeClear()
    external fun nativeTick()
    external fun nativeOutcome():Int
    fun publish(p: Presence): Boolean {
        val artwork = if (p.art == PRISM_LOGO) p.art else publicArtwork(p.art, p.identity.lastOrNull().orEmpty())
        nativePublish(p.title.take(128).toByteArray(Charsets.UTF_8), p.detail.take(128).toByteArray(Charsets.UTF_8),
            artwork.toByteArray(Charsets.UTF_8), p.watching, p.start, p.end, p.activityName.toByteArray(Charsets.UTF_8))
        return artwork.isNotEmpty()
    }
}
