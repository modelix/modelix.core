package org.modelix.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Records every bulk call so tests can assert how many rounds happened and which keys were batched together. */
private class RecordingSource(private val backing: Map<Int, String>) : IBulkExecutor<Int, String> {
    val calls = mutableListOf<List<Int>>()

    override fun execute(keys: List<Int>): Map<Int, String> {
        calls.add(keys.sorted())
        return keys.filter { backing.containsKey(it) }.associateWith { backing.getValue(it) }
    }

    override suspend fun executeSuspending(keys: List<Int>): Map<Int, String> = execute(keys)

    val roundCount: Int get() = calls.size
    val totalKeysFetched: Int get() = calls.sumOf { it.size }
}

/**
 * Verifies the round-based engine's batching behaviour through the real [BulkRequestStreamExecutor] / [enqueue] path:
 * independent fetches share a round, dependent fetches use separate rounds, duplicate keys are fetched once, and deep
 * dependent chains stay stack-safe.
 */
class BulkRequestBatchingTest {
    private val data = (0..1000).associateWith { "v$it" }

    @Test
    fun single_fetch() {
        val source = RecordingSource(data)
        val executor = BulkRequestStreamExecutor(source)
        assertEquals("v3", executor.query { executor.enqueue(3).exceptionIfEmpty() })
        assertEquals(1, source.roundCount)
        assertEquals(listOf(listOf(3)), source.calls)
    }

    @Test
    fun independent_fetches_are_batched_into_one_round() {
        val source = RecordingSource(data)
        val executor = BulkRequestStreamExecutor(source)
        val result = executor.query {
            IStream.many(listOf(1, 2, 3, 4)).flatMapOrdered { executor.enqueue(it).orNull() }.toList()
        }
        assertEquals(listOf("v1", "v2", "v3", "v4"), result)
        assertEquals(1, source.roundCount)
        assertEquals(listOf(listOf(1, 2, 3, 4)), source.calls)
    }

    @Test
    fun zip_batches_both_sides_together() {
        val source = RecordingSource(data)
        val executor = BulkRequestStreamExecutor(source)
        val combined = executor.query {
            executor.enqueue(5).exceptionIfEmpty().zipWith(executor.enqueue(6).exceptionIfEmpty()) { a, b -> "$a+$b" }
        }
        assertEquals("v5+v6", combined)
        assertEquals(1, source.roundCount)
        assertEquals(listOf(listOf(5, 6)), source.calls)
    }

    @Test
    fun dependent_fetches_use_separate_rounds() {
        val source = RecordingSource(data)
        val executor = BulkRequestStreamExecutor(source)
        // The second fetch's key is derived from the first result -> it cannot be known until round 1 resolves.
        val result = executor.query {
            executor.enqueue(1).exceptionIfEmpty().flatMapOne { first ->
                executor.enqueue(first.removePrefix("v").toInt() + 4).exceptionIfEmpty() // "v1" -> 5
            }
        }
        assertEquals("v5", result)
        assertEquals(2, source.roundCount)
        assertEquals(listOf(listOf(1), listOf(5)), source.calls)
    }

    @Test
    fun duplicate_keys_are_fetched_once() {
        val source = RecordingSource(data)
        val executor = BulkRequestStreamExecutor(source)
        val result = executor.query {
            IStream.many(listOf(2, 2, 2, 7, 7)).flatMapOrdered { executor.enqueue(it).orNull() }.toList()
        }
        assertEquals(listOf("v2", "v2", "v2", "v7", "v7"), result)
        assertEquals(1, source.roundCount)
        assertEquals(2, source.totalKeysFetched) // only keys 2 and 7 actually fetched
    }

    @Test
    fun missing_key_resolves_to_empty() {
        val source = RecordingSource(data)
        val executor = BulkRequestStreamExecutor(source)
        assertEquals(null, executor.query { executor.enqueue(9999).orNull() })
        assertFailsWith<NoSuchElementException> {
            executor.query<String> { executor.enqueue(9999).exceptionIfEmpty() }
        }
    }

    @Test
    fun deep_dependent_chain_is_stack_safe() {
        val source = RecordingSource(data)
        val executor = BulkRequestStreamExecutor(source)
        // 1000 sequential dependent fetches -> 1000 extra rounds, must not overflow the stack.
        var stream: IStream.One<String> = executor.enqueue(0).exceptionIfEmpty()
        repeat(1000) {
            stream = stream.flatMapOne { v -> executor.enqueue(v.removePrefix("v").toInt() + 1).exceptionIfEmpty() }
        }
        assertEquals("v1000", executor.query { stream })
        assertEquals(1001, source.roundCount)
        assertTrue(source.calls.all { it.size == 1 })
    }
}
