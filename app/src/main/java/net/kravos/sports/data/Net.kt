package net.kravos.sports.data

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** HTTP GET with in-memory TTL cache, in-flight de-duplication and a disk copy for instant/offline paint. */
class Net(context: Context) {
    private val disk = File(context.cacheDir, "api").apply { mkdirs() }
    private val mem = LruCache<String, Pair<Long, String>>(400)
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val gate = Semaphore(8)
    @Volatile var lastError: String? = null
        private set

    suspend fun get(url: String, ttl: Int, allowStale: Boolean = true): String {
        val now = System.currentTimeMillis()
        mem.get(url)?.let { (t, body) -> if (now - t < ttl * 1000L) return body }
        inflight[url]?.let { return it.await() }
        val d = CompletableDeferred<String>()
        val prev = inflight.putIfAbsent(url, d)
        if (prev != null) return prev.await()
        try {
            val body = gate.withPermit { Http.get(url) }
            mem.put(url, now to body)
            if (ttl >= 60) runCatching { file(url).writeText(body) }
            lastError = null
            d.complete(body)
            return body
        } catch (e: Exception) {
            lastError = "${url.substringAfter("//").substringBefore('/')}: ${e.message}"
            val stale = mem.get(url)?.second ?: if (allowStale) runCatching { file(url).takeIf { it.exists() }?.readText() }.getOrNull() else null
            if (stale != null) { d.complete(stale); return stale }
            d.completeExceptionally(e)
            throw e
        } finally {
            inflight.remove(url)
        }
    }

    private fun file(url: String) = File(disk, url.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(180))
}
