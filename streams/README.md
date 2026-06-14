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
sealed class Step<out T>                          // a node in a lazily-evaluated computation
class Done<T>(val values: List<T>)                // fully resolved
class FetchStep<T>(source, key)                   // a bulk-fetch leaf
class MapStep / FlatMapStep / ZipStep / FanStep   // transform / dependency / applicative join / fan-out
// + AsyncStep, RecoverStep, OnErrorStep, MemoStep
```

A `Step` is a node the driver walks. The combinators encode the applicative/monadic split:

- `flatMapStep` (monadic) — defers the continuation into the next round.
- `combineConcat` / `zipN` / `fanOut` (applicative) — let the requests of independent branches be collected into the
  same round. That *is* the batch.

Combinators evaluate **eagerly when their inputs are already `Done`** (the synchronous fast path for local data: no
node allocated, side effects in `map`/`flatMap` lambdas run at build time in build order). A lazy node is built only
when an input would block on a request.

## Execution

The driver (`Execution.drive` / `driveSuspending`) is a loop where each iteration is one batch round:

1. **walk** the live node graph, collecting the first-unmet request of every pending branch into a shared pool;
2. issue **one bulk call per data source** (chunked to that source's `IBulkExecutor.batchSize`), run any async leaves,
   and fill the per-run cache (so each key is fetched at most once — dedup within *and* across rounds);
3. walk again, until the root resolves.

The loop is the **trampoline** across rounds, so fetch-dependent depth costs rounds, not stack.

### Depth-first, bounded frontier

The walk does **not** expand the whole breadth of a traversal at once. It stops adding requests to the round once the
pool reaches the source's `batchSize` (the cap), and `FanStep` children are built lazily — instantiated left-to-right
and dropped once resolved — so the number of live *unresolved* children never exceeds the cap either. Once a request
resolves, the next walk descends deeper before expanding shallower siblings that didn't fit. The result is a
depth-first traversal whose **peak request frontier is `≤ batchSize`**, independent of how wide a tree level is — the
property the old `BulkRequestStreamExecutor.RequestQueue.sendNextBatch` provided. Sibling requests still share a round
whenever they fit under the cap (the common case), so batching is preserved; with a cap above any level the behaviour
collapses to one round per level. (See `FrontierSizeTest`.)

Batching is **structural**: a fetch leaf carries its own data source, so the driver groups fetches per source per
round regardless of which executor runs it. `BulkRequestStreamExecutor.enqueue(key)` is simply a fetch leaf bound to
its `IBulkExecutor`. Because the source is carried by the stream, the **batch size lives on `IBulkExecutor`** (not on
the executor), and **terminal operations don't need an executor**: `getBlocking()` / `getSuspending()` /
`iterateBlocking { }` / `iterateSuspending { }` / `executeBlocking()` drive the self-contained stream directly. The
executor-taking overloads are `@Deprecated` and kept for compatibility.

## Public API

Cardinality is encoded in the type: `IStream.Many` (0+), `IStream.ZeroOrOne` (0..1), `IStream.OneOrMany` (1+),
`IStream.One` (exactly 1), and `IStream.Completable` (completion, no value).

- Builders: `IStream.of`, `empty`, `many`, `zip`, `fromFlow`, `singleFromCoroutine`, …
- Terminals (no executor needed): `getBlocking()`, `getSuspending()`, `iterateBlocking { }`,
  `iterateSuspending { }`, `executeBlocking()`. The `*(executor)` overloads remain, deprecated.
- Executors (still available; `BulkRequestStreamExecutor` also provides `enqueue`): `IStreamExecutor`,
  `SimpleStreamExecutor`, `BulkRequestStreamExecutor`, `IExecutableStream`.
- Batchable source: `IBulkExecutor<K, V>` (`execute` + `executeSuspending` + `batchSize`).

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

1. `iterate` / `iterateSuspending` fully materialize the *result* before visiting — higher peak memory for very large
   iterations. Note this is independent of the request frontier, which **is** bounded (see "Depth-first, bounded
   frontier" above): the fetch working set stays `≤ batchSize` even though the result list does not. The clean fix for
   the result side, if a hot path needs it, is per-round streaming in just the `iterate*` drivers.
2. `cached()` multicasts: a stream consumed by multiple branches is evaluated once per run (via a shared memo cell
   resolved once and reused), so side effects and work aren't duplicated. ModelQL relies on this for shared query steps.
3. `take` / `skip` operate on materialized results (don't prune upstream fetches).
4. Within-round stack safety covers fetch-dependent chains (the common case). A pathological deep *pure* `flatMap`
   chain that never blocks would still recurse; the fix is to encode `Step` as a stack-safe free monad if needed.
