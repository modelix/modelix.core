package org.modelix.streams.engine

import kotlinx.coroutines.yield
import org.modelix.streams.IBulkExecutor

/**
 * The round-based interpreter that backs every [org.modelix.streams.IStream] instance.
 *
 * A [Step] is a node in a lazily-evaluated computation that produces zero or more values. Evaluation proceeds in
 * *rounds*: the driver walks the live node graph, collecting the data requests it depends on, resolves them with one
 * bulk call per source, and walks again — until the root resolves. The combinators encode the applicative/monadic split
 * that makes bulk-request batching automatic:
 *  - applicative composition ([combineConcat], [zipN], [fanOut]) lets the independent requests of sibling branches be
 *    collected into the *same* round — that is the batch;
 *  - monadic composition ([flatMapStep]) introduces a dependency, pushing the right-hand side into a *later* round.
 *
 * ### Depth-first, bounded frontier
 *
 * The driver does not expand the whole breadth of a traversal at once. A single walk collects the first-unmet request
 * of every pending branch into a shared pool, but **stops adding requests once the pool reaches the source's
 * [IBulkExecutor.batchSize]** (the cap). Fan-out children ([FanStep]) are built lazily, left-to-right, and dropped once
 * resolved, so the number of live *unresolved* child nodes never exceeds the cap either. Once a branch's request
 * resolves, the next walk descends one level deeper into it before expanding the breadth of shallower siblings that did
 * not fit under the cap. The result is a depth-first traversal whose peak request frontier is `<= batchSize`,
 * regardless of how wide a tree level is — the property the old `BulkRequestStreamExecutor.RequestQueue.sendNextBatch`
 * provided. Independent sibling requests still share a round whenever they fit under the cap (the common case), so
 * applicative batching is preserved; with a cap above any level the behaviour collapses to a single round per level.
 *
 * Errors are not represented as nodes: an exception thrown while resolving a node propagates natively and is caught by
 * an enclosing [recover]/[doOnError]. A fetch that throws inside the driver propagates out of [drive] uncaught, exactly
 * as before.
 */
internal sealed class Step<out T> {
    /** Memoized resolved value list; `null` until this node is fully resolved within the current execution. */
    var cached: List<Any?>? = null
}

/** An already-resolved node. */
internal class Done<T>(val values: List<T>) : Step<T>()

/** A fetch leaf with zero-or-one semantics: a `null`/absent value resolves to an empty list. */
internal class FetchStep<T>(val source: IBulkExecutor<Any?, Any?>, val key: Any?) : Step<T>()

/** A leaf that produces its values via a (potentially suspending) computation rather than a bulk fetch. */
internal class AsyncStep<T>(
    val token: Any,
    val produceBlocking: () -> List<Any?>,
    val produceSuspending: suspend () -> List<Any?>,
) : Step<T>()

/** Linear transform of the resolved value list of [src]. */
internal class MapStep<A, T>(val src: Step<A>, val f: (List<A>) -> List<T>) : Step<T>()

/** Monadic dependency: once [src] resolves, the values pick the next step. */
internal class FlatMapStep<A, T>(val src: Step<A>, val f: (List<A>) -> Step<T>) : Step<T>() {
    var inner: Step<T>? = null
}

/** Applicative join: all parts resolve, then their value lists are handed to [f] together. */
internal class ZipStep<A, T>(val parts: List<Step<A>>, val f: (List<List<A>>) -> List<T>) : Step<T>()

/**
 * Lazily-built ordered concatenation of [size] children. The only place a breadth frontier is born. Children are
 * instantiated on demand via [makeChild] (left-to-right) and dropped once resolved, so the live unresolved children are
 * bounded by the round cap.
 */
internal class FanStep<T>(val size: Int, val makeChild: (Int) -> Step<T>) : Step<T>() {
    val childResults: Array<List<Any?>?> = arrayOfNulls(size)
    val childNodes: Array<Step<T>?> = arrayOfNulls(size)
}

/** Recovers from an exception thrown while resolving [src] by producing replacement values. */
internal class RecoverStep<T>(val src: Step<T>, val handler: (Throwable) -> List<T>) : Step<T>()

/** Runs [consumer] before an exception thrown while resolving [src] propagates. */
internal class OnErrorStep<T>(val src: Step<T>, val consumer: (Throwable) -> Unit) : Step<T>()

/** A view onto a shared, single-resolution node (the [org.modelix.streams.IStream.One.cached] mechanism). */
internal class MemoStep<T>(val token: Any, val init: () -> Step<Any?>) : Step<T>()

// ---------------------------------------------------------------------------------------------------------------------
// Combinators (the API surface used by StreamImpl; signatures are kept stable)
// ---------------------------------------------------------------------------------------------------------------------

// Each combinator evaluates *eagerly* when its inputs are already resolved (`Done`), exactly as the previous engine
// did, so the synchronous prefix of a computation — including any side effects in `map`/`flatMap` lambdas — runs at
// build time and in build order. Some call sites rely on this (e.g. a `var` set by one synchronous stream and read by a
// later `deferZeroOrOne`). Only when an input would block on a request is a lazy node built and deferred to the driver.

internal fun <A, B> Step<A>.mapValues(f: (List<A>) -> List<B>): Step<B> =
    if (this is Done) Done(f(values)) else MapStep(this, f)

internal fun <A, B> Step<A>.flatMapStep(f: (List<A>) -> Step<B>): Step<B> =
    if (this is Done) f(values) else FlatMapStep(this, f)

internal fun <T> Step<T>.recover(handler: (Throwable) -> List<T>): Step<T> =
    if (this is Done) this else RecoverStep(this, handler)

internal fun <T> Step<T>.doOnError(consumer: (Throwable) -> Unit): Step<T> =
    if (this is Done) this else OnErrorStep(this, consumer)

/** Applicative concatenation of the values of independent, already-built steps in order. */
internal fun <T> combineConcat(steps: List<Step<T>>): Step<T> =
    if (steps.all { it is Done }) Done(steps.flatMap { (it as Done<T>).values }) else FanStep(steps.size) { steps[it] }

/** Applicative combination handing all resolved value lists to [f] together. */
internal fun <T, R> zipN(steps: List<Step<T>>, f: (List<List<T>>) -> List<R>): Step<R> =
    if (steps.all { it is Done }) Done(f(steps.map { (it as Done<T>).values })) else ZipStep(steps, f)

/**
 * Applicative concatenation of children built lazily from [items] — the breadth-bounded fan-out.
 *
 * The leading run of children that resolve synchronously (to a [Done]) is built and concatenated eagerly, preserving
 * the build-time evaluation of a synchronous prefix. At the first child that would block on a request, the rest of the
 * children become a lazy [FanStep]: instantiated on demand and dropped once resolved, so the live unresolved children
 * stay bounded by the round cap. A wide traversal whose children are all fetches has an empty eager prefix and is
 * therefore fully lazy.
 */
internal fun <A, T> fanOut(items: List<A>, makeChild: (A) -> Step<T>): Step<T> {
    if (items.isEmpty()) return Done(emptyList())
    val prefix = ArrayList<T>()
    var i = 0
    var firstBlocking: Step<T>? = null
    while (i < items.size) {
        val child = makeChild(items[i])
        if (child is Done) {
            prefix.addAll(child.values)
            i++
        } else {
            firstBlocking = child
            break
        }
    }
    if (firstBlocking == null) return Done(prefix) // every child was synchronous
    val startIndex = i
    val alreadyBuilt = firstBlocking
    val lazyTail = FanStep(items.size - startIndex) { idx ->
        if (idx == 0) alreadyBuilt else makeChild(items[startIndex + idx])
    }
    return if (prefix.isEmpty()) lazyTail else FanStep(2) { idx -> if (idx == 0) Done(prefix) else lazyTail }
}

// ---------------------------------------------------------------------------------------------------------------------
// Leaves
// ---------------------------------------------------------------------------------------------------------------------

/** A fetch leaf. The [execution] is unused at build time; the cache is consulted by the driver while it walks. */
@Suppress("UNUSED_PARAMETER")
internal fun fetchStep(execution: Execution, source: IBulkExecutor<Any?, Any?>, key: Any?): Step<Any?> =
    FetchStep(source, key)

@Suppress("UNUSED_PARAMETER")
internal fun asyncStep(
    execution: Execution,
    token: Any,
    produceBlocking: () -> List<Any?>,
    produceSuspending: suspend () -> List<Any?>,
): Step<Any?> = AsyncStep(token, produceBlocking, produceSuspending)

@Suppress("UNUSED_PARAMETER")
internal fun memoStep(execution: Execution, token: Any, init: () -> Step<Any?>): Step<Any?> = MemoStep(token, init)

// ---------------------------------------------------------------------------------------------------------------------
// Per-run state and the depth-first, bounded-frontier driver
// ---------------------------------------------------------------------------------------------------------------------

private val MISSING = Any()

/**
 * Per-query state shared by every [Step] built for a single execution. Holds the fetch cache (each key fetched at most
 * once across the whole traversal), async results, and shared memo cells, plus the mutable state of the current round.
 */
internal class Execution {
    private val fetchCaches = HashMap<IBulkExecutor<Any?, Any?>, HashMap<Any?, Any?>>()
    private val asyncResults = HashMap<Any, List<Any?>>()
    private val memoCells = HashMap<Any, Step<Any?>>()

    // Mutable state of the round currently being collected; reset by [newRound].
    private var pendingFetches = LinkedHashMap<IBulkExecutor<Any?, Any?>, LinkedHashSet<Any?>>()
    private var pendingAsync = ArrayList<AsyncStep<*>>()
    private val seenAsyncTokens = HashSet<Any>()
    private var pendingCount = 0
    private var cap = Int.MAX_VALUE

    private fun cacheFor(source: IBulkExecutor<Any?, Any?>) = fetchCaches.getOrPut(source) { HashMap() }

    private fun memoCell(token: Any, init: () -> Step<Any?>): Step<Any?> = memoCells.getOrPut(token, init)

    private fun newRound() {
        pendingFetches = LinkedHashMap()
        pendingAsync = ArrayList()
        seenAsyncTokens.clear()
        pendingCount = 0
        cap = Int.MAX_VALUE
    }

    private fun roundIsFull(): Boolean = pendingCount >= cap

    /** Registers a fetch demand for the current round, honoring the per-source batch-size cap. */
    private fun addFetch(source: IBulkExecutor<Any?, Any?>, key: Any?) {
        val set = pendingFetches.getOrPut(source) { LinkedHashSet() }
        if (key in set) return
        cap = if (cap == Int.MAX_VALUE) source.batchSize else minOf(cap, source.batchSize)
        if (pendingCount >= cap) return
        set.add(key)
        pendingCount++
    }

    private fun addAsync(step: AsyncStep<*>) {
        if (seenAsyncTokens.add(step.token)) pendingAsync.add(step)
    }

    /**
     * Returns the node's resolved value list, or `null` if it is still blocked on a request that was registered for the
     * current round. Recurses on the node graph; the recursion depth is the traversal depth (bounded in practice),
     * matching the recursion characteristics of the previous engine.
     */
    private fun expand(step: Step<*>): List<Any?>? {
        step.cached?.let { return it }
        val result: List<Any?>? = when (step) {
            is Done -> step.values
            is FetchStep -> {
                val cache = cacheFor(step.source)
                if (cache.containsKey(step.key)) {
                    val v = cache[step.key]
                    if (v === MISSING) emptyList() else listOf(v)
                } else {
                    addFetch(step.source, step.key)
                    null
                }
            }
            is AsyncStep -> {
                if (asyncResults.containsKey(step.token)) {
                    asyncResults.getValue(step.token)
                } else {
                    addAsync(step)
                    null
                }
            }
            is MapStep<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                expand(step.src)?.let { (step.f as (List<Any?>) -> List<Any?>)(it) }
            }
            is FlatMapStep<*, *> -> {
                val sv = expand(step.src)
                if (sv == null) {
                    null
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val typed = step as FlatMapStep<Any?, Any?>
                    val inner = typed.inner ?: typed.f(sv).also { typed.inner = it }
                    expand(inner)
                }
            }
            is ZipStep<*, *> -> {
                var allResolved = true
                val partValues = ArrayList<List<Any?>>(step.parts.size)
                for (part in step.parts) {
                    val pv = expand(part)
                    if (pv == null) allResolved = false else if (allResolved) partValues.add(pv)
                }
                if (allResolved) {
                    @Suppress("UNCHECKED_CAST")
                    (step.f as (List<List<Any?>>) -> List<Any?>)(partValues)
                } else {
                    null
                }
            }
            is FanStep<*> -> {
                @Suppress("UNCHECKED_CAST")
                val fan = step as FanStep<Any?>
                val out = ArrayList<Any?>()
                var allResolved = true
                var i = 0
                while (i < fan.size) {
                    val done = fan.childResults[i]
                    if (done != null) {
                        if (allResolved) out.addAll(done)
                        i++
                        continue
                    }
                    val child = fan.childNodes[i] ?: fan.makeChild(i).also { fan.childNodes[i] = it }
                    val cv = expand(child)
                    if (cv != null) {
                        fan.childResults[i] = cv
                        fan.childNodes[i] = null // free the resolved child node
                        if (allResolved) out.addAll(cv)
                    } else {
                        allResolved = false
                        // Keep scanning siblings to batch their requests into this same round, until the cap is hit.
                        if (roundIsFull()) break
                    }
                    i++
                }
                if (allResolved) out else null
            }
            is RecoverStep<*> -> {
                try {
                    expand(step.src)
                } catch (ex: Throwable) {
                    @Suppress("UNCHECKED_CAST")
                    (step.handler as (Throwable) -> List<Any?>)(ex)
                }
            }
            is OnErrorStep<*> -> {
                try {
                    expand(step.src)
                } catch (ex: Throwable) {
                    step.consumer(ex)
                    throw ex
                }
            }
            is MemoStep<*> -> expand(memoCell(step.token, step.init))
        }
        if (result != null) step.cached = result
        return result
    }

    /**
     * Drives a step to completion, blocking. Each loop iteration is one round: collect the bounded request frontier,
     * resolve it (one bulk call per source, chunked to that source's [IBulkExecutor.batchSize], plus any async leaves),
     * then walk again. The loop is the trampoline across rounds; fetch-dependent depth costs rounds, not stack.
     */
    fun <T> drive(initial: Step<T>): List<T> {
        while (true) {
            newRound()
            val resolved = expand(initial)
            if (resolved != null) {
                @Suppress("UNCHECKED_CAST")
                return resolved as List<T>
            }
            check(pendingFetches.isNotEmpty() || pendingAsync.isNotEmpty()) {
                "stream made no progress: root unresolved but no request was demanded"
            }
            for ((source, keys) in pendingFetches) {
                for (chunk in keys.chunked(source.batchSize)) {
                    fillFetch(source, chunk, source.execute(chunk))
                }
            }
            for (action in pendingAsync) {
                asyncResults[action.token] = action.produceBlocking()
            }
        }
    }

    suspend fun <T> driveSuspending(initial: Step<T>): List<T> {
        while (true) {
            newRound()
            val resolved = expand(initial)
            if (resolved != null) {
                @Suppress("UNCHECKED_CAST")
                return resolved as List<T>
            }
            check(pendingFetches.isNotEmpty() || pendingAsync.isNotEmpty()) {
                "stream made no progress: root unresolved but no request was demanded"
            }
            for ((source, keys) in pendingFetches) {
                for (chunk in keys.chunked(source.batchSize)) {
                    fillFetch(source, chunk, source.executeSuspending(chunk))
                }
            }
            for (action in pendingAsync) {
                asyncResults[action.token] = action.produceSuspending()
            }
            yield()
        }
    }

    private fun fillFetch(source: IBulkExecutor<Any?, Any?>, keys: List<Any?>, results: Map<Any?, Any?>) {
        val cache = cacheFor(source)
        for (key in keys) cache[key] = if (results.containsKey(key)) results[key] else MISSING
    }
}
