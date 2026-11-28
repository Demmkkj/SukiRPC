package dev.nuvio.presence.ui.renaissance

// Stable IDs also used by the existing widgets, settings and control activity.
internal val primaryRoutes = listOf(0, 9, 5, 8, 3)
internal fun primaryRoute(tab:Int):Int = when(tab){
    1 -> 9
    2, 4, 6, 7 -> 3
    else -> if(tab in primaryRoutes) tab else 0
}
internal fun parentRoute(tab:Int):Int? = if(tab in listOf(1,2,4,6,7)) primaryRoute(tab) else null
