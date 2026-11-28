package dev.nuvio.presence

// ClearRichPresence has no acknowledgment. Keep pumping the SDK after each
// request, with bounded retries; never report remote removal as confirmed.
internal suspend fun drainRichPresence(
    clear: () -> Unit, tick: () -> Unit, pause: suspend (Long) -> Unit
) {
    repeat(40) { step ->
        if (step == 0 || step == 10 || step == 25) clear()
        tick()
        pause(100L)
    }
    tick()
}

// Main-thread only. A newly published activity cancels old clear retries.
// Repeated empty samples must not restart the retry window.
internal class RpcClearRetries {
    private var startedAt = 0L
    private var next = 2
    private val offsets = longArrayOf(1000L, 2500L)
    fun request(now: Long, clear: () -> Unit) {
        if (next < offsets.size) return
        startedAt = now
        next = 0
        clear()
    }
    fun tick(now: Long, clear: () -> Unit) {
        if (next >= offsets.size || now - startedAt < offsets[next]) return
        // Skip missed deadlines rather than emitting a burst after a suspended process.
        while (next < offsets.size && now - startedAt >= offsets[next]) next++
        clear()
    }
    fun cancel() { next = offsets.size }
}
