package dev.nuvio.presence

import android.content.Context

internal object Settings {
    fun prefs(c:Context)=c.getSharedPreferences("preferences",0)
    fun options(c:Context)=prefs(c).let{Options(it.getBoolean("art",true),it.getBoolean("time",true),it.getBoolean("watch",true),it.getBoolean("clearPaused",false),it.getBoolean("episode",true))}
}
