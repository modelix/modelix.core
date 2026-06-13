# streams2

A small, dependency-free streaming abstraction for **batched, lazy data loading**. It is a clean-room
reimplementation of the `streams` module's execution model, built around a single round-based interpreter
instead of three interchangeable reactive backends.

This module is the **reference implementation / prototype** of the design. The same design has been ported into
the `streams` module to back its (richer, legacy) public API — see
[`streams-redesign.md`](../streams-redesign.md) at the repo root.

## Why a custom implementation

The one capability that justifies not using `Flow`/`RxJava`/coroutines directly is **automatic bulk-request
batching**: when a traversal needs many objects from a remote store, the independent requests should be coalesced
into a single round-trip rather than fetched one at a time.

That requires the runtime to *see a whole set of independent data requests before forcing any of them*. This is an
**applicative** property, not a monadic one:

- `zip(a, b)` and the elements of a `Many` are **independent** → all their fetches belong to the **same batch round**.
- `flatMap` is a **dependency** → the right-hand side's keys can't be known until the left resolves → **next round**.

Pull-based streams (`Sequence`, `Flow`) are demand-driven and sequential: they force one element, block, then ask for
the next. They cannot expose the request frontier without spawning a coroutine per pending item (too much overhead).
A push-based library (Reaktive) can, which is why the old `streams` module carried it. streams2 gets the same
property **structurally**, from the shape of the computation, with no external dependency.

Prior art for this idea: Haxl (Haskell), ZIO Query / `ZQuery` (Scala), Stitch, Clump.

## Scope (intentional limitations)

- **No incremental emission.** A stream is fully materialized when queried; `take`/`skip` truncate the materialized
  result rather than stopping upstream work.
- **No coroutine/Flow integration.** Execution is synchronous and blocking.

(The port into `streams` re-adds a suspending driver and async leaves for `fromFlow`/`singleFromCoroutine`, because
its legacy API requires them. The streams2 engine itself stays minimal.)

## The core idea: `Step`

Everything hinges on one internal type that encodes the applicative/monadic split mechanically (the ZIO Query
`Result` trick). See [`Step.kt`](src/commonMain/kotlin/org/modelix/streams2/Step.kt).

```kotlin
sealed interface Step<out T>
class Done<T>(val values: List<T>) : Step<T>                              // fully resolved
class Blocked<T>(val requests: RequestMap, val resume: () -> Step<T>)     // needs a batch first
class Failed(val cause: Throwable) : Step<Nothing>
```

A `Step` produces 0+ values of `T`. Evaluation proceeds in **rounds**. The combinators are where batching falls out:

```kotlin
// MONADIC: dependency. Right side's requests aren't known until left is Done → next round.
fun <A, B> Step<A>.flatMapStep(f: (List<A>) -> Step<B>): Step<B>

// APPLICATIVE: independence. Request sets UNION into the SAME round. This is the batch.
fun <T> combineConcat(steps: List<Step<T>>): Step<T>
fun <T, R> zipN(steps: List<Step<T>>, f: (List<List<T>>) -> List<R>): Step<R>
```

A subtree containing no `Blocked` resolves straight to `Done` — the **synchronous fast path** for locally-available
data, with no scheduler and no request set allocated.

## Execution

A `DataSource` is an [`IBulkExecutor<K, V>`](src/commonMain/kotlin/org/modelix/streams2/IBulkExecutor.kt):

```kotlin
interface IBulkExecutor<K, V> {
    fun execute(keys: List<K>): Map<K, V>
}
```

The driver ([`Execution.drive`](src/commonMain/kotlin/org/modelix/streams2/Execution.kt)) is a loop where each
iteration is one batch round: issue **one bulk call per source**, fill the per-run cache (dedup within *and* across
rounds), then resume. The loop is the **trampoline** that keeps fetch-dependent chains stack-safe regardless of depth.

```kotlin
val executor = StreamExecutor()
val source: IBulkExecutor<Int, String> = ...

// independent fetches -> ONE round
val pair = executor.query(IStream.fetch(source, 1).zipWith(IStream.fetch(source, 2)) { a, b -> "$a+$b" })

// dependent fetches -> SEPARATE rounds
val chained = executor.query(
    IStream.fetch(source, 1).flatMapOne { v -> IStream.fetch(source, keyFrom(v)) },
)
```

## Public API

Cardinality is encoded in the type — see [`IStream.kt`](src/commonMain/kotlin/org/modelix/streams2/IStream.kt):

| Type                  | Meaning      |
|-----------------------|--------------|
| `IStream.Many<E>`     | 0 or more    |
| `IStream.ZeroOrOne<E>`| 0 or 1       |
| `IStream.One<E>`      | exactly 1    |

Builders (`IStream.Companion`): `of`, `empty`, `many`, `fetch`, `zip`.
Executor ([`IStreamExecutor`](src/commonMain/kotlin/org/modelix/streams2/IStreamExecutor.kt)): `query`, `queryAll`,
`iterate`.

Operators: `map`, `mapNotNull`, `filter`, `flatMap`, `concat`, `distinct`, `take`, `skip`, `withIndex`, `fold`,
`toList`, `toMap`, `count`, `isEmpty`, `drainAll`, `assertEmpty`, `firstOrEmpty`, `firstOrDefault`, `exactlyOne`,
`orNull`, `ifEmpty`, `exceptionIfEmpty`, `flatMapZeroOrOne`, `flatMapOne`, `zipWith`.

## Layout

```
src/commonMain/kotlin/org/modelix/streams2/
├── Step.kt             // Step IR + applicative/monadic combinators
├── Execution.kt        // per-run fetch cache, RequestMap, the round driver
├── IBulkExecutor.kt    // batchable data source
├── IStream.kt          // public cardinality types + StreamBase impl + builders
└── IStreamExecutor.kt  // materializes streams
```

## Known limitations / future work

1. **Within-round stack safety.** The round driver trampolines across `Blocked` (the common fetch-dependent case).
   A pathological deep *pure* `flatMap` chain that never blocks would still recurse natively; the fix is to encode
   `Step` itself as a stack-safe free monad (an explicit interpreter loop) if that ever bites.
2. **Error-recovery operators** (`onErrorReturn`-style) aren't exposed yet; the `Failed` channel exists in the IR.
3. **`take`/`skip` don't prune upstream fetches** (a consequence of no incremental emission).
