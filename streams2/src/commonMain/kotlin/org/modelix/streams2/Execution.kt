package org.modelix.streams2

/** The deduped set of pending fetches for a single round, grouped by data source. */
internal class RequestMap private constructor(
    val byExecutor: Map<IBulkExecutor<Any?, Any?>, Set<Any?>>,
) {
    val isEmpty: Boolean get() = byExecutor.isEmpty()

    fun union(other: RequestMap): RequestMap {
        if (byExecutor.isEmpty()) return other
        if (other.byExecutor.isEmpty()) return this
        val result = HashMap<IBulkExecutor<Any?, Any?>, MutableSet<Any?>>()
        for ((executor, keys) in byExecutor) result.getOrPut(executor) { HashSet() }.addAll(keys)
        for ((executor, keys) in other.byExecutor) result.getOrPut(executor) { HashSet() }.addAll(keys)
        return RequestMap(result)
    }

    companion object {
        val EMPTY = RequestMap(emptyMap())

        @Suppress("UNCHECKED_CAST")
        fun single(executor: IBulkExecutor<*, *>, key: Any?): RequestMap =
            RequestMap(mapOf((executor as IBulkExecutor<Any?, Any?>) to setOf(key)))
    }
}

/** Sentinel stored in the cache for keys that were requested but absent from the bulk response. */
private val MISSING = Any()

/**
 * Per-run state shared by every [Step] built for a single query. Holds the fetched-value cache so that the same key
 * is fetched at most once across the whole traversal (dedup within and across rounds), regardless of how many stream
 * branches request it.
 */
internal class Execution {
    private val caches = HashMap<IBulkExecutor<Any?, Any?>, HashMap<Any?, Any?>>()

    @Suppress("UNCHECKED_CAST")
    private fun cacheFor(executor: IBulkExecutor<*, *>): HashMap<Any?, Any?> =
        caches.getOrPut(executor as IBulkExecutor<Any?, Any?>) { HashMap() }

    fun isCached(executor: IBulkExecutor<*, *>, key: Any?): Boolean = cacheFor(executor).containsKey(key)

    fun cachedValue(executor: IBulkExecutor<*, *>, key: Any?): Any? {
        val value = cacheFor(executor)[key]
        if (value === MISSING) throw NoSuchElementException("No value returned for key: $key")
        return value
    }

    fun fill(executor: IBulkExecutor<Any?, Any?>, requestedKeys: Set<Any?>, results: Map<Any?, Any?>) {
        val cache = cacheFor(executor)
        for (key in requestedKeys) {
            cache[key] = if (results.containsKey(key)) results[key] else MISSING
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <K, V> fetchStep(execution: Execution, source: IBulkExecutor<K, V>, key: K): Step<V> {
    return if (execution.isCached(source, key)) {
        Done(listOf(execution.cachedValue(source, key) as V))
    } else {
        Blocked(RequestMap.single(source, key)) { fetchStep(execution, source, key) }
    }
}

/**
 * Drives a [Step] to completion. Each iteration of the loop is one batch round: it issues a single bulk call per
 * pending data source, fills the cache, then resumes. The loop is the trampoline that keeps fetch-dependent chains
 * (the common case in tree traversal) stack-safe regardless of depth.
 */
internal fun <T> Execution.drive(initial: Step<T>): List<T> {
    var step = initial
    while (true) {
        when (val current = step) {
            is Done -> return current.values
            is Failed -> throw current.cause
            is Blocked -> {
                for ((source, keys) in current.requests.byExecutor) {
                    val results = source.execute(keys.toList()) as Map<Any?, Any?>
                    fill(source, keys, results)
                }
                step = current.resume()
            }
        }
    }
}
