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
    external fun nativeClear()
    external fun nativeTick()
    external fun nativeOutcome():Int
    fun publish(p:Presence){nativePublish(p.title.take(128).toByteArray(Charsets.UTF_8),p.detail.take(128).toByteArray(Charsets.UTF_8),p.art.toByteArray(Charsets.UTF_8),p.watching,p.start,p.end,p.activityName.toByteArray(Charsets.UTF_8))}
}
