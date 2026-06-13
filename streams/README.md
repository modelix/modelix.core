# streams

A streaming abstraction for **batched, lazy data loading**, built around a single round-based interpreter.

It provides the `IStream` API used throughout modelix for traversing large, content-addressed models where the data
is loaded on demand from a (possibly remote) store. Its defining capability is **automatic bulk-request batching**:
independent data requests reachable in a traversal are coalesced into a single round-trip instead of being fetched
one at a time.

> History: this module previously had three interchangeable backends (Sequence / Flow / Reaktive). They were replaced
> by the round-based `Step` engine described below. See [`streams-redesign.md`](../streams-redesign.md) for the
> redesign and migration record.

## Why a custom implementation

Batching requires the runtime to *see a set of independent data requests before forcing any of them*. This is an
**applicative** property, not a monadic one:

- `zip(a, b)` and the elements of a `Many` are **independent** → all their fetches belong to the **same batch round**.
- `flatMap*` is a **dependency** → the right-hand side's keys can't be known until the left resolves → **next round**.

Pull-based streams (`Sequence`, `Flow`) are demand-driven and sequential: they force one element, block, then ask for
the next. They cannot expose the request frontier without spawning a coroutine per pending item (too much overhead).
A push-based library (Reaktive) can, which is why this module used to carry it. The `Step` engine gets the same
property **structurally**, from the shape of the computation, with no external dependency.

Prior art for this idea: Haxl (Haskell), ZIO Query / `ZQuery` (Scala), Stitch, Clump.

## The core idea: `Step`

The engine ([`engine/StepEngine.kt`](src/commonMain/kotlin/org/modelix/streams/engine/StepEngine.kt)) describes a
stream lazily and interprets it in **rounds**:

```kotlin
sealed interface Step<out T>
class Done<T>(val values: List<T>)                                // fully resolved
class Blocked<T>(val pending: Pending, val resume: () -> Step<T>) // needs a round of work first
class Failed(val cause: Throwable)
```

`Pending` is the deduplicated work for one round: bulk fetches grouped by data source, plus async leaves. The
combinators encode the applicative/monadic split:

- `flatMapStep` (monadic) — defers the continuation into the next round.
- `combineConcat` / `zipN` / `zip2` (applicative) — **union** the pending work of independent branches into the same
  round. That union *is* the batch.

A subtree with no `Blocked` resolves straight to `Done`: the **synchronous fast path** for local data, with no
scheduler and nothing allocated.

## Execution

The driver (`Execution.drive` / `driveSuspending`) is a loop where each iteration is one batch round:

1. issue **one bulk call per data source** (chunked to `batchSize`), and run any async leaves;
2. fill the per-run cache (so each key is fetched at most once — dedup within *and* across rounds);
3. `resume()` and repeat until `Done`.

The loop is the **trampoline** that keeps fetch-dependent chains stack-safe regardless of depth.

Batching is **structural**: a fetch leaf carries its own data source, so the driver groups fetches per source per
round regardless of which executor runs it. `BulkRequestStreamExecutor.enqueue(key)` is simply a fetch leaf bound to
its `IBulkExecutor`.

## Public API

Cardinality is encoded in the type: `IStream.Many` (0+), `IStream.ZeroOrOne` (0..1), `IStream.OneOrMany` (1+),
`IStream.One` (exactly 1), and `IStream.Completable` (completion, no value).

- Builders: `IStream.of`, `empty`, `many`, `zip`, `fromFlow`, `singleFromCoroutine`, …
- Execution: `IStreamExecutor` (`query`, `iterate`, suspending variants), `SimpleStreamExecutor`,
  `BulkRequestStreamExecutor`, `IExecutableStream`.
- Batchable source: `IBulkExecutor<K, V>` (`execute` + `executeSuspending`).

## Layout

```
src/commonMain/kotlin/org/modelix/streams/
├── engine/StepEngine.kt          // Step IR, combinators, drivers, fetch + async leaves
├── StreamImpl.kt                 // backing impl for every cardinality + Completable + the builder
├── IStream.kt                    // public cardinality types + operators/extensions
├── IStreamBuilder.kt             // builder interface (of/many/zip/fromFlow/…)
├── IStreamExecutor.kt            // executor interface + extensions
├── IExecutableStream.kt          // deferred/composable execution
├── IStreamInternal.kt            // @DelicateModelixApi blocking/suspending accessors
├── BulkRequestStreamExecutor.kt  // batching executor + IBulkExecutor
├── SimpleStreamExecutor.kt
└── Zip.kt                        // arity-2..9 zip overloads
```

## Known limitations / tradeoffs

These follow from the engine resolving each query fully (no incremental emission):

1. `iterate` / `iterateSuspending` fully materialize before visiting — higher peak memory for very large iterations.
   The clean fix, if a hot path needs it, is per-round streaming in just the `iterate*` drivers.
2. `cached()` is currently a no-op; fetch-level dedup (the expensive part) is handled by the per-run cache.
3. `take` / `skip` operate on materialized results (don't prune upstream fetches).
4. Within-round stack safety covers fetch-dependent chains (the common case). A pathological deep *pure* `flatMap`
   chain that never blocks would still recurse; the fix is to encode `Step` as a stack-safe free monad if needed.
