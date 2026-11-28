package dev.nuvio.presence

import kotlin.coroutines.*

fun main() {
    var elapsed = 0L
    val cleared = mutableListOf<Long>()
    val ticks = mutableListOf<Long>()
    var finished = false
    suspend {
        drainRichPresence({ cleared.add(elapsed); Unit }, { ticks.add(elapsed); Unit }, { elapsed += it })
    }.startCoroutine(object : Continuation<Unit> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) { result.getOrThrow(); finished = true }
    })
    check(finished)
    check(cleared == listOf(0L, 1000L, 2500L))
    check(ticks.size == 41 && ticks.last() == 4000L)
    check(elapsed == 4000L)
    val retries = RpcClearRetries()
    val attempts = mutableListOf<Long>()
    fun request(at: Long) = retries.request(at) { attempts.add(at) }
    fun tick(at: Long) = retries.tick(at) { attempts.add(at) }
    request(0); request(500); tick(999); tick(1000); tick(2499); tick(2500); tick(10000)
    check(attempts == listOf(0L, 1000L, 2500L))
    request(11000); retries.cancel(); tick(12000); tick(13500)
    check(attempts.last() == 11000L)
    request(14000); tick(18000); tick(19000)
    check(attempts.takeLast(2) == listOf(14000L, 18000L))
    println("PASS: bounded RPC clear retries, cancellation and callback drain")
}
