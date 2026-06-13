package org.modelix.streams2

/**
 * The internal intermediate representation of a partially-evaluated stream.
 *
 * A [Step] produces zero or more values of [T]. Evaluation proceeds in *rounds*: a [Step] is either fully resolved
 * ([Done]), needs a batch of data fetches before it can continue ([Blocked]), or has failed ([Failed]).
 *
 * The combinators on [Step] encode the applicative/monadic split that makes batching automatic:
 * - Applicative composition ([zipN], [combineConcat]) unions the pending requests of independent branches into the
 *   *same* round. That is the batch.
 * - Monadic composition ([flatMapStep]) introduces a dependency, so the right-hand side's requests can only be
 *   discovered after the left side resolves, i.e. in a *later* round.
 *
 * Nothing is ever forced eagerly: a subtree that contains no [Blocked] resolves straight to [Done] without ever
 * allocating a request set, which is the synchronous fast path for locally-available data.
 */
internal sealed interface Step<out T>

internal class Done<T>(val values: List<T>) : Step<T>

internal class Blocked<T>(val requests: RequestMap, val resume: () -> Step<T>) : Step<T>

internal class Failed(val cause: Throwable) : Step<Nothing>

/** Transform the resolved value list, threading through the round boundaries. */
internal fun <A, B> Step<A>.mapValues(f: (List<A>) -> List<B>): Step<B> = when (this) {
    is Done -> Done(f(values))
    is Blocked -> Blocked(requests) { resume().mapValues(f) }
    is Failed -> this
}

/** Monadic bind: the dependency that introduces a round boundary. */
internal fun <A, B> Step<A>.flatMapStep(f: (List<A>) -> Step<B>): Step<B> = when (this) {
    is Done -> f(values)
    is Blocked -> Blocked(requests) { resume().flatMapStep(f) }
    is Failed -> this
}

/**
 * Applicative combination of independent steps that concatenates their values in order.
 *
 * Iterates over [steps] within a single round (no per-element native recursion); only recurses once per round via
 * the [Blocked] thunk, so the recursion depth is bounded by the number of fetch rounds rather than the number of
 * elements.
 */
internal fun <T> combineConcat(steps: List<Step<T>>): Step<T> {
    var allDone = true
    var requests = RequestMap.EMPTY
    for (step in steps) {
        when (step) {
            is Failed -> return step
            is Blocked -> {
                allDone = false
                requests = requests.union(step.requests)
            }
            is Done -> {}
        }
    }
    if (allDone) return Done(steps.flatMap { (it as Done).values })
    return Blocked(requests) { combineConcat(steps.map { if (it is Blocked) it.resume() else it }) }
}

/** Applicative combination that hands all resolved value lists to [f] together. */
internal fun <T, R> zipN(steps: List<Step<T>>, f: (List<List<T>>) -> List<R>): Step<R> {
    var allDone = true
    var requests = RequestMap.EMPTY
    for (step in steps) {
        when (step) {
            is Failed -> return step
            is Blocked -> {
                allDone = false
                requests = requests.union(step.requests)
            }
            is Done -> {}
        }
    }
    if (allDone) return Done(f(steps.map { (it as Done).values }))
    return Blocked(requests) { zipN(steps.map { if (it is Blocked) it.resume() else it }, f) }
}

internal fun <A, B, C> zip2(a: Step<A>, b: Step<B>, f: (List<A>, List<B>) -> List<C>): Step<C> {
    if (a is Failed) return a
    if (b is Failed) return b
    if (a is Done && b is Done) return Done(f(a.values, b.values))
    var requests = RequestMap.EMPTY
    if (a is Blocked) requests = requests.union(a.requests)
    if (b is Blocked) requests = requests.union(b.requests)
    return Blocked(requests) {
        zip2(if (a is Blocked) a.resume() else a, if (b is Blocked) b.resume() else b, f)
    }
}
