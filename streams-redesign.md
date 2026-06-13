# Streams redesign & migration

This document records the redesign of the `streams` module: replacing its three interchangeable reactive backends
(Sequence / Flow / Reaktive) with a single round-based interpreter, while preserving the public API so that no
downstream module required API-driven changes.

The engine was first prototyped in a separate `streams2` module and then merged into `streams` (the prototype's
parallel `IStream` API and engine were redundant once the design was proven). See [`streams/README.md`](streams/README.md)
for the resulting design overview; this document covers the redesign and the migration of the wider codebase.

---

## 1. Motivation

The `streams` module existed to provide one capability that plain coroutines/`Flow` could not give cheaply:
**automatic bulk-request batching** for lazy, partial loading of large content-addressed models. When a traversal
needs many objects from a remote store, the independent requests must be coalesced into a single round-trip.

### Why there were three backends

The old design carried three implementations because they had different execution semantics
(see the former doc-comment in `IStreamExecutor`):

- **Sequence** — synchronous, eager, pull-based; minimal overhead when all data is local.
- **Flow** — suspendable, pull-based; best integration with `suspend` sources.
- **Reaktive** — push-based; the *only* one able to collect a set of pending requests before forcing any of them,
  which is what bulk batching needs. Flows could parallelize requests only by launching a coroutine per object —
  too much overhead.

This meant ~1900 lines across `SequenceStreamBuilder`, `FlowStreamBuilder`, `ReaktiveStreamBuilder`,
`DeferredStreamBuilder`, the concrete stream classes, and the Reaktive third-party dependency.

### The key insight

Batching requires the runtime to **see a set of independent data requests before forcing any of them**. That is an
**applicative** property, not a monadic one:

- `zip(a, b)` and the elements of a `Many` are **independent** → their fetches belong to the **same batch round**.
- `flatMap` is a **dependency** → the right-hand side's keys are unknown until the left resolves → a **later round**.

Pull (Sequence/Flow) is demand-driven and sequential and cannot expose this frontier without per-item concurrency.
Push (Reaktive) can. But a purpose-built interpreter gets the same property **structurally**, from the shape of the
computation, with no external dependency — and collapses all three backends into one.

Prior art for this pattern: Haxl (Haskell), ZIO Query / `ZQuery` (Scala), Stitch, Clump.

---

## 2. The new design

### `Step` — the intermediate representation

A stream is described lazily and interpreted in **rounds**. The core type
([`streams/.../engine/StepEngine.kt`](streams/src/commonMain/kotlin/org/modelix/streams/engine/StepEngine.kt)):

```kotlin
sealed interface Step<out T>
class Done<T>(val values: List<T>)                          // fully resolved
class Blocked<T>(val pending: Pending, val resume: () -> Step<T>)  // needs a round of work first
class Failed(val cause: Throwable)
```

`Pending` is the deduplicated work for one round: bulk fetches grouped by data source, plus async leaves. The
combinators encode the applicative/monadic split:

- `flatMapStep` (monadic) — defers the continuation into the next round.
- `combineConcat` / `zipN` / `zip2` (applicative) — **union** the pending work of independent branches into the same
  round. That union *is* the batch.

A subtree with no `Blocked` resolves straight to `Done`: the **synchronous fast path** for local data, with no
scheduler and nothing allocated (this subsumes the old Sequence backend's role).

### The driver

`Execution.drive` (and `driveSuspending`) is a loop where each iteration is one batch round:

1. For each data source in `pending`, issue **one bulk call** (`execute` / `executeSuspending`), chunked to `batchSize`.
2. Run any async leaves.
3. Fill the per-run cache (so each key is fetched at most once — dedup within *and* across rounds).
4. `resume()` and repeat until `Done`.

The loop is the **trampoline** that keeps fetch-dependent chains (the common case in tree traversal) stack-safe
regardless of depth. This subsumes the old Reaktive subscribe-collect-batch mechanism.

### Batching is now structural

Previously, batching was tied to an executor via a `ContextValue<RequestQueue>`: `enqueue(key)` registered into the
current query's queue. Now a fetch leaf carries its own data **source**, and the driver groups fetches **per source
per round**. Consequences:

- `BulkRequestStreamExecutor.enqueue(key)` is simply a fetch leaf bound to its `IBulkExecutor`.
- Any executor that drives a stream batches its fetches — including `SimpleStreamExecutor`, which therefore now issues
  **strictly fewer** round-trips than before, never more.

---

## 3. How the public API was preserved

The entire public surface of `streams` is unchanged. The interface files (`IStream`, `IStreamBuilder`,
`IStreamExecutor`, `IExecutableStream`, `IStreamInternal`, `IBulkExecutor`, `Zip`, and all extension functions) were
kept as-is. Only the *implementation* was swapped:

| Concern | Before | After |
|---|---|---|
| Stream instances | `SingleValueStream`, `CollectionAsStream`, `EmptyStream`, deferred wrappers, … | one `StreamImpl` backing every cardinality, plus `CompletableImpl` |
| Builder | `DeferredStreamBuilder` (+ 3 backend builders) | one `StreamBuilderImpl` over the engine |
| `SimpleStreamExecutor` / `BlockingStreamExecutor` | Sequence/Flow conversion | thin wrappers over `drive` / `driveSuspending` |
| `BulkRequestStreamExecutor` | Reaktive + `RequestQueue` + `CompletableObservable` | round driver; `enqueue` = fetch leaf |
| `convert(IStreamBuilder)` | converts to a backend representation | returns `this` (single representation) |
| `asFlow()` / `asSequence()` | backend conversion | materialize via the driver |
| `fromFlow` / `singleFromCoroutine` | Flow/Reaktive sources | **async leaves** resolved by the suspending driver (or `runBlockingIfJvm` under the blocking driver) |

New files in `streams`:

- [`engine/StepEngine.kt`](streams/src/commonMain/kotlin/org/modelix/streams/engine/StepEngine.kt) — `Step`, `Pending`,
  combinators, blocking + suspending drivers, fetch leaves, async leaves.
- [`StreamImpl.kt`](streams/src/commonMain/kotlin/org/modelix/streams/StreamImpl.kt) — `StreamImpl`, `CompletableImpl`,
  `StreamBuilderImpl`, `StreamAssertionError`.

Deleted: `SequenceStreamBuilder`, `FlowStreamBuilder`, `ReaktiveStreamBuilder`, `DeferredStreamBuilder`,
`SingleValueStream`, `CollectionAsStream`, `SequenceAsStream`, `EmptyStream`, `EmptyCompletableStream`,
`CompletableObservable`, `StreamExtensions` (Reaktive helpers). The Reaktive dependency was removed from
`streams/build.gradle.kts`.

### Why a single module (the `streams2` prototype was merged in)

The engine was prototyped in a separate `streams2` module, then merged into `streams` rather than kept as a separate
dependency, because:

- the engine types are `internal` to `streams`;
- the real integration needs a **suspending driver** and **async leaves** that the minimal prototype deliberately
  omitted;
- `streams`' `IBulkExecutor` already declares `executeSuspending`, which the engine uses directly;
- keeping both meant two competing `IBulkExecutor` / `IStream` hierarchies for no benefit — the prototype's API was a
  strict subset of the one `streams` already exposes.

The prototype's batching/dedup/stack-safety tests were kept, rewritten against the real `BulkRequestStreamExecutor`
API (`BulkRequestBatchingTest`).

---

## 4. Migration of the wider codebase

**No downstream module required API-driven changes.** The only edits outside `streams` were removals of **dead code**
that compiled solely because `streams` used to re-export Reaktive via an `api` dependency:

- `model-api/.../async/NodeAsAsyncNode.kt` — two unused private helpers (`asOptionalMono`, `asMono`) and their imports.
- `modelql-untyped/.../AllChildrenTraversalStep.kt`, `AllReferencesTraversalStep.kt`, `DescendantsTraversalStep.kt` —
  unused `com.badoo.reaktive.observable.{map, flatMap}` imports (the `.map`/`.flatMap` calls resolve to `IStream`
  members).

These were latent couplings to a transitive dependency, not uses of the `streams` API.

### Verification

- `streams`: compiles JVM + JS; unit tests pass.
- `datastructures`, `model-datastructure`: compile **untouched**; full JVM test suites pass.
- `modelql-core`, `modelql-untyped`: compile + tests pass.
- `model-client`, `model-server`, `model-server-api`, `modelql-typed/-html/-client/-server`, `bulk-model-sync-lib`:
  compile (JVM + JS where applicable).
- **`model-server` `LazyLoadingTest`** passes — the access-pattern / batching-correctness test, which confirms the
  structural batching reproduces the old Reaktive collect-and-batch behavior.

---

## 5. Follow-up: the executor is no longer required to run a stream

Because batching is now structural — a fetch leaf carries its own `IBulkExecutor` source — a `Step` is fully
self-contained. The `IStreamExecutor` passed to the terminal operations no longer carries any batching context; it
only supplied a batch size and (for `BulkRequestStreamExecutor`) set `IStreamExecutor.CONTEXT`. Two changes removed
that residual coupling:

- **Batch size moved onto the source.** `IBulkExecutor` now declares `val batchSize` (default
  `DEFAULT_BULK_REQUEST_BATCH_SIZE = 5000`); the driver chunks each source's keys to *its own* batch size. The driver
  no longer takes a batch-size parameter. `BulkRequestStreamExecutor(source, batchSize)` keeps working — it exposes
  the constructor batch size on the source the leaves bind to.
- **Executor-less terminals.** New `getBlocking()` / `getSuspending()` / `iterateBlocking { }` /
  `iterateSuspending { }` / `executeBlocking()` / `executeSuspending()` drive a fresh execution directly. The
  `*(executor: IStreamExecutor | IStreamExecutorProvider)` overloads are `@Deprecated(ReplaceWith(...))` and keep their
  original behavior for compatibility.

All in-repo call sites (~180, mostly `getBlocking`) were migrated to the executor-less form. `IStreamExecutor` /
`IStreamExecutorProvider` and `BulkRequestStreamExecutor.enqueue` remain — `enqueue` is still how fetch leaves are
created, and `IStreamExecutor.CONTEXT` is still set during `BulkRequestStreamExecutor` runs because ModelQL resolves
the "current executor" via `IStreamExecutor.getInstance()`.

---

## 6. Behavioral tradeoffs

These follow from the "no incremental emission" decision and are intentional. They change performance characteristics,
not results.

1. **`iterate` / `iterateSuspending` fully materialize before visiting.** Reaktive previously pushed elements and
   drained between batches to bound memory; the new driver builds the whole result list first. For very large
   server-side iterations (e.g. walking all objects) this raises peak memory. *If this matters for a hot path, the
   clean fix is to add genuine per-round streaming to just the `iterate*` drivers without disturbing the rest of the
   engine.*
2. **`cached()` multicasts (evaluates once per run).** A stream consumed by multiple branches is resolved once via a
   shared memo cell (`MemoCell`) that the round driver advances at most once per round, so work *and side effects* are
   not duplicated and the result is shared. ModelQL depends on this for shared/side-effecting query steps (see
   `ModelQLClientTest.testCaching`).
3. **`take` / `skip` operate on materialized results** — they do not prune upstream fetches.
4. **`SimpleStreamExecutor` now batches** per source/round — strictly fewer round-trips than before.

## 7. Known limitations / future work

- **Within-round stack safety.** The round driver trampolines across `Blocked` (the common fetch-dependent case). A
  pathological deep *pure* `flatMap` chain that never blocks would still recurse natively; the fix is to encode `Step`
  as a stack-safe free monad (explicit interpreter loop) if needed.
- **Optional streaming `iterate*`** — see tradeoff #1.
- **Retire the executor entirely.** With the executor no longer required to run a stream (§5), `IStreamExecutor` /
  `IStreamExecutorProvider` could be removed over time — the remaining users are `enqueue` (fetch-leaf creation) and
  ModelQL's `getInstance()`-based "current executor" lookup, both of which can be reworked.
