package org.modelix.streams2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Records every bulk call so tests can assert how many rounds happened and which keys were batched together. */
private class RecordingSource(private val backing: Map<Int, String>) : IBulkExecutor<Int, String> {
    val calls = mutableListOf<List<Int>>()

    override fun execute(keys: List<Int>): Map<Int, String> {
        calls.add(keys.sorted())
        return keys.associateWith { backing.getValue(it) }
    }

    val roundCount: Int get() = calls.size
    val totalKeysFetched: Int get() = calls.sumOf { it.size }
}

class Streams2Test {
    private val executor = StreamExecutor()
    private val data = (0..9).associateWith { "v$it" }

    @Test
    fun constant_stream_does_not_touch_a_source() {
        // The eager fast path: no Fetch nodes means the round loop returns immediately.
        val result = executor.query(IStream.of(21).map { it * 2 })
        assertEquals(42, result)
    }

    @Test
    fun many_operators() {
        val stream = IStream.many(1..5).map { it * 10 }.filter { it > 20 }
        assertEquals(listOf(30, 40, 50), executor.queryAll(stream))
        assertEquals(120, executor.query(IStream.many(1..5).fold(0) { acc, v -> acc + v }.map { it * 8 }))
    }

    @Test
    fun single_fetch() {
        val source = RecordingSource(data)
        assertEquals("v3", executor.query(IStream.fetch(source, 3)))
        assertEquals(1, source.roundCount)
        assertEquals(listOf(listOf(3)), source.calls)
    }

    @Test
    fun independent_fetches_are_batched_into_one_round() {
        val source = RecordingSource(data)
        val stream = IStream.many(listOf(1, 2, 3, 4))
            .flatMap { key -> IStream.fetch(source, key) }
        assertEquals(listOf("v1", "v2", "v3", "v4"), executor.queryAll(stream))
        assertEquals(1, source.roundCount)
        assertEquals(listOf(listOf(1, 2, 3, 4)), source.calls)
    }

    @Test
    fun zip_batches_both_sides_together() {
        val source = RecordingSource(data)
        val combined = IStream.fetch(source, 5).zipWith(IStream.fetch(source, 6)) { a, b -> "$a+$b" }
        assertEquals("v5+v6", executor.query(combined))
        assertEquals(1, source.roundCount)
        assertEquals(listOf(listOf(5, 6)), source.calls)
    }

    @Test
    fun dependent_fetches_use_separate_rounds() {
        val source = RecordingSource(data)
        // The second fetch's key is derived from the first result -> it cannot be known until round 1 resolves.
        val stream = IStream.fetch(source, 1).flatMapOne { first ->
            val nextKey = first.removePrefix("v").toInt() + 4 // "v1" -> 5
            IStream.fetch(source, nextKey)
        }
        assertEquals("v5", executor.query(stream))
        assertEquals(2, source.roundCount)
        assertEquals(listOf(listOf(1), listOf(5)), source.calls)
    }

    @Test
    fun duplicate_keys_are_fetched_once() {
        val source = RecordingSource(data)
        val stream = IStream.many(listOf(2, 2, 2, 7, 7))
            .flatMap { key -> IStream.fetch(source, key) }
        assertEquals(listOf("v2", "v2", "v2", "v7", "v7"), executor.queryAll(stream))
        assertEquals(1, source.roundCount)
        assertEquals(2, source.totalKeysFetched) // only keys 2 and 7 actually fetched
    }

    @Test
    fun zero_or_one_handling() {
        assertEquals("default", executor.query(IStream.empty<String>().ifEmpty { "default" }))
        assertEquals(null, executor.query(IStream.empty<String>().orNull()))
        assertEquals("x", executor.query(IStream.of("x").orNull()))
        assertFailsWith<IllegalStateException> {
            executor.query(IStream.empty<String>().exceptionIfEmpty { IllegalStateException("empty") })
        }
    }

    @Test
    fun missing_key_surfaces_as_error() {
        val source = RecordingSource(data)
        assertFailsWith<NoSuchElementException> {
            executor.query(IStream.fetch(source, 999))
        }
    }

    @Test
    fun deep_dependent_chain_is_stack_safe() {
        val source = RecordingSource((0..1000).associateWith { "v$it" })
        // 1000 sequential dependent fetches -> 1000 rounds, must not overflow the stack.
        var stream: IStream.One<String> = IStream.fetch(source, 0)
        repeat(1000) {
            stream = stream.flatMapOne { v ->
                val next = v.removePrefix("v").toInt() + 1
                IStream.fetch(source, next)
            }
        }
        assertEquals("v1000", executor.query(stream))
        assertEquals(1001, source.roundCount)
        assertTrue(source.calls.all { it.size == 1 })
    }
}
