package dev.nuvio.presence.ui.renaissance
fun main(){
    check(primaryRoutes.size==5 && primaryRoutes.distinct().size==5)
    primaryRoutes.forEach{check(primaryRoute(it)==it && parentRoute(it)==null)}
    check(primaryRoute(1)==9 && parentRoute(1)==9)
    listOf(2,4,6,7).forEach{check(primaryRoute(it)==3 && parentRoute(it)==3)}
    check(primaryRoute(-1)==0 && parentRoute(-1)==null)
    println("PASS: Renaissance routes and parent navigation")
}
