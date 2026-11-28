package dev.nuvio.presence

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.Build

internal object RpcAppDetection {
    fun allowed(c:Context):Boolean {
        val ops=c.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.packageName)==AppOpsManager.MODE_ALLOWED
    }
    // Only the selected package match is returned; no app history is stored or transmitted.
    fun matches(c:Context, pkg:String):Boolean {
        if(!allowed(c))return false
        val now=System.currentTimeMillis();val manager=c.getSystemService(UsageStatsManager::class.java)
        val events=manager.queryEvents(now-24*60*60*1000L,now)?:return false
        val event=UsageEvents.Event();var foreground=""
        while(events.hasNextEvent()) {
            events.getNextEvent(event)
            if(event.eventType==UsageEvents.Event.MOVE_TO_FOREGROUND)foreground=event.packageName.orEmpty()
            if(event.eventType==UsageEvents.Event.MOVE_TO_BACKGROUND&&event.packageName==foreground)foreground=""
            if(Build.VERSION.SDK_INT>=28 && event.eventType==UsageEvents.Event.SCREEN_NON_INTERACTIVE)foreground=""
        }
        return foreground==pkg && c.getSystemService(android.os.PowerManager::class.java).isInteractive
    }
}
