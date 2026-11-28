package dev.nuvio.presence

/** Cache successful lookups and failures separately from the active session. */
internal class ArtworkCache<K, V>(private val capacity: Int, private val now: () -> Long) {
    private data class Entry<V>(val value: V, val expires: Long)
    private val entries = LinkedHashMap<K, Entry<V>>(capacity, .75f, true)

    operator fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry.expires > now()) return entry.value
        entries.remove(key)
        return null
    }

    fun put(key: K, value: V, ttl: Long) {
        entries[key] = Entry(value, now() + ttl)
        while (entries.size > capacity) {
            val first = entries.entries.iterator()
            first.next()
            first.remove()
        }
    }

    fun clear() = entries.clear()
}
