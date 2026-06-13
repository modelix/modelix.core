package org.modelix.streams.engine

import kotlinx.coroutines.yield
import org.modelix.streams.IBulkExecutor

/**
 * The round-based interpreter that backs every [org.modelix.streams.IStream] instance.
 *
 * A [Step] produces zero or more values. Evaluation proceeds in *rounds*: a step is [Done], [Failed], or [Blocked]
 * on a set of pending work ([Pending]) that must be resolved before it can continue. The combinators encode the
 * applicative/monadic split that makes bulk-request batching automatic:
 *  - applicative composition ([combineConcat], [zipN], [zip2]) unions the pending work of independent branches into
 *    the *same* round — that is the batch;
 *  - monadic composition ([flatMapStep]) introduces a dependency, pushing the right-hand side into a *later* round.
 *
 * A subtree with no pending work resolves straight to [Done] without allocating anything — the synchronous fast path.
 */
internal sealed interface Step<out T>

internal class Done<T>(val values: List<T>) : Step<T>

internal class Blocked<T>(val pending: Pending, val resume: () -> Step<T>) : Step<T>

internal class Failed(val cause: Throwable) : Step<Nothing>

/** A leaf that produces its values via a (potentially suspending) computation rather than a bulk fetch. */
internal class AsyncAction(
    val token: Any,
    val produceBlocking: () -> List<Any?>,
    val produceSuspending: suspend () -> List<Any?>,
)

/** The deduplicated set of work pending for a single round: bulk fetches grouped by source, plus async leaves. */
internal class Pending private constructor(
    val fetches: Map<IBulkExecutor<Any?, Any?>, Set<Any?>>,
    val asyncActions: List<AsyncAction>,
) {
    fun union(other: Pending): Pending {
        if (this === EMPTY) return other
        if (other === EMPTY) return this
        val fetches = HashMap<IBulkExecutor<Any?, Any?>, MutableSet<Any?>>()
        for ((source, keys) in this.fetches) fetches.getOrPut(source) { HashSet() }.addAll(keys)
        for ((source, keys) in other.fetches) fetches.getOrPut(source) { HashSet() }.addAll(keys)
        return Pending(fetches, this.asyncActions + other.asyncActions)
    }

    companion object {
        val EMPTY = Pending(emptyMap(), emptyList())

        @Suppress("UNCHECKED_CAST")
        fun fetch(source: IBulkExecutor<*, *>, key: Any?): Pending =
            Pending(mapOf((source as IBulkExecutor<Any?, Any?>) to setOf(key)), emptyList())

        fun async(action: AsyncAction): Pending = Pending(emptyMap(), listOf(action))
    }
}

internal fun <A, B> Step<A>.mapValues(f: (List<A>) -> List<B>): Step<B> = when (this) {
    is Done -> Done(f(values))
    is Blocked -> Blocked(pending) { resume().mapValues(f) }
    is Failed -> this
}

internal fun <A, B> Step<A>.flatMapStep(f: (List<A>) -> Step<B>): Step<B> = when (this) {
    is Done -> f(values)
    is Blocked -> Blocked(pending) { resume().flatMapStep(f) }
    is Failed -> this
}

/** Recover from a failure (or an exception thrown while resuming) by producing replacement values. */
internal fun <T> Step<T>.recover(handler: (Throwable) -> List<T>): Step<T> = when (this) {
    is Done -> this
    is Failed -> Done(handler(cause))
    is Blocked -> Blocked(pending) {
        try {
            resume().recover(handler)
        } catch (ex: Throwable) {
            Done(handler(ex))
        }
    }
}

/** Run [consumer] before a failure (or thrown exception) propagates. */
internal fun <T> Step<T>.doOnError(consumer: (Throwable) -> Unit): Step<T> = when (this) {
    is Done -> this
    is Failed -> {
        consumer(cause)
        this
    }
    is Blocked -> Blocked(pending) {
        try {
            resume().doOnError(consumer)
        } catch (ex: Throwable) {
            consumer(ex)
            throw ex
        }
    }
}

/** Applicative combination concatenating the values of independent steps in order, batching their pending work. */
internal fun <T> combineConcat(steps: List<Step<T>>): Step<T> {
    var pending = Pending.EMPTY
    var allDone = true
    for (step in steps) {
        when (step) {
            is Failed -> return step
            is Blocked -> {
                allDone = false
                pending = pending.union(step.pending)
            }
            is Done -> {}
        }
    }
    if (allDone) return Done(steps.flatMap { (it as Done).values })
    return Blocked(pending) { combineConcat(steps.map { if (it is Blocked) it.resume() else it }) }
}

/** Applicative combination handing all resolved value lists to [f] together. */
internal fun <T, R> zipN(steps: List<Step<T>>, f: (List<List<T>>) -> List<R>): Step<R> {
    var pending = Pending.EMPTY
    var allDone = true
    for (step in steps) {
        when (step) {
            is Failed -> return step
            is Blocked -> {
                allDone = false
                pending = pending.union(step.pending)
            }
            is Done -> {}
        }
    }
    if (allDone) return Done(f(steps.map { (it as Done).values }))
    return Blocked(pending) { zipN(steps.map { if (it is Blocked) it.resume() else it }, f) }
}

// ---------------------------------------------------------------------------------------------------------------------
// Per-run state and leaves
// ---------------------------------------------------------------------------------------------------------------------

private val MISSING = Any()

/**
 * Per-query state shared by every [Step] built for a single execution. Holds the fetch cache so that each key is
 * fetched at most once across the whole traversal (dedup within and across rounds), and the results of async leaves.
 */
internal class Execution {
    private val fetchCaches = HashMap<IBulkExecutor<Any?, Any?>, HashMap<Any?, Any?>>()
    private val asyncResults = HashMap<Any, List<Any?>>()

    @Suppress("UNCHECKED_CAST")
    private fun cacheFor(source: IBulkExecutor<*, *>) =
        fetchCaches.getOrPut(source as IBulkExecutor<Any?, Any?>) { HashMap() }

    fun isFetched(source: IBulkExecutor<*, *>, key: Any?): Boolean = cacheFor(source).containsKey(key)

    fun fetchedValue(source: IBulkExecutor<*, *>, key: Any?): Any? {
        val value = cacheFor(source)[key]
        return if (value === MISSING) null else value
    }

    fun fillFetch(source: IBulkExecutor<Any?, Any?>, keys: Set<Any?>, results: Map<Any?, Any?>) {
        val cache = cacheFor(source)
        for (key in keys) cache[key] = if (results.containsKey(key)) results[key] else MISSING
    }

    fun hasAsync(token: Any): Boolean = asyncResults.containsKey(token)
    fun asyncResult(token: Any): List<Any?> = asyncResults.getValue(token)
    fun fillAsync(token: Any, values: List<Any?>) { asyncResults[token] = values }
}

/** A fetch leaf with zero-or-one semantics: a `null`/absent value resolves to an empty step. */
internal fun fetchStep(execution: Execution, source: IBulkExecutor<Any?, Any?>, key: Any?): Step<Any?> {
    if (execution.isFetched(source, key)) {
        val value = execution.fetchedValue(source, key)
        return if (value == null) Done(emptyList()) else Done(listOf(value))
    }
    return Blocked(Pending.fetch(source, key)) { fetchStep(execution, source, key) }
}

internal fun asyncStep(
    execution: Execution,
    token: Any,
    produceBlocking: () -> List<Any?>,
    produceSuspending: suspend () -> List<Any?>,
): Step<Any?> {
    if (execution.hasAsync(token)) return Done(execution.asyncResult(token))
    return Blocked(Pending.async(AsyncAction(token, produceBlocking, produceSuspending))) {
        asyncStep(execution, token, produceBlocking, produceSuspending)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Drivers
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Drives a step to completion blocking. Each loop iteration is one round: one bulk call per source (chunked to that
 * source's [IBulkExecutor.batchSize]) plus any async leaves, then resume. The loop is the trampoline that keeps
 * fetch-dependent chains stack-safe regardless of depth.
 */
internal fun <T> Execution.drive(initial: Step<T>): List<T> {
    var step = initial
    while (true) {
        when (val current = step) {
            is Done -> return current.values
            is Failed -> throw current.cause
            is Blocked -> {
                for ((source, keys) in current.pending.fetches) {
                    for (chunk in keys.chunked(source.batchSize)) {
                        @Suppress("UNCHECKED_CAST")
                        val results = source.execute(chunk) as Map<Any?, Any?>
                        fillFetch(source, chunk.toSet(), results)
                    }
                }
                for (action in current.pending.asyncActions) {
                    fillAsync(action.token, action.produceBlocking())
                }
                step = current.resume()
            }
        }
    }
}

internal suspend fun <T> Execution.driveSuspending(initial: Step<T>): List<T> {
    var step = initial
    while (true) {
        when (val current = step) {
            is Done -> return current.values
            is Failed -> throw current.cause
            is Blocked -> {
                for ((source, keys) in current.pending.fetches) {
                    for (chunk in keys.chunked(source.batchSize)) {
                        @Suppress("UNCHECKED_CAST")
                        val results = source.executeSuspending(chunk) as Map<Any?, Any?>
                        fillFetch(source, chunk.toSet(), results)
                    }
                }
                for (action in current.pending.asyncActions) {
                    fillAsync(action.token, action.produceSuspending())
                }
                yield()
                step = current.resume()
            }
        }
    }
}

private fun <T> Set<T>.chunked(size: Int): List<List<T>> =
    if (this.size <= size) listOf(this.toList()) else this.toList().chunked(size)
