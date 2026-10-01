package org.mlm.mages.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mlm.mages.LinkPreview
import org.mlm.mages.matrix.MatrixPort

/**
 * Fetches previews once per URL per session; misses are cached too, because the
 * endpoint is rate-limited and every miss makes the homeserver crawl the target.
 */
class LinkPreviewLoader(
    val port: MatrixPort,
    private val maxCacheEntries: Int = 512,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(2))

    private val mu = Mutex()

    private val inFlight = HashMap<String, Deferred<LinkPreview?>>()

    // Keyed by URL; a null value is a remembered miss, told apart from an absent key.
    private val cache = LinkedHashMap<String, LinkPreview?>(maxCacheEntries)

    suspend fun resolve(url: String): LinkPreview? {
        mu.withLock {
            if (cache.containsKey(url)) {
                val cached = cache.remove(url)
                cache[url] = cached
                return cached
            }
        }

        val existing = mu.withLock { inFlight[url] }
        if (existing != null) return existing.await()

        // Runs in the loader's scope so one caller cancelling does not drop another's request.
        val deferred = scope.async {
            runCatching { port.getLinkPreview(url) }.getOrNull()
        }
        mu.withLock { inFlight[url] = deferred }

        val result = try {
            deferred.await()
        } finally {
            mu.withLock { inFlight.remove(url) }
        }

        mu.withLock {
            cache[url] = result
            while (cache.size > maxCacheEntries) {
                val oldest = cache.entries.iterator()
                if (!oldest.hasNext()) break
                oldest.next()
                oldest.remove()
            }
        }

        return result
    }

    fun shutdown() {
        scope.cancel()
    }
}
