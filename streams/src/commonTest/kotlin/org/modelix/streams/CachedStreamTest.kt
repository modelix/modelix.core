package org.modelix.streams

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the multicast semantics of [IStream.One.cached]: a stream consumed by multiple downstream branches is
 * evaluated exactly once (including any side effects), and the single result is shared. This is what ModelQL relies on
 * for shared/memoized query steps (see ModelQLClientTest.testCaching).
 */
class CachedStreamTest {
    private val executor = SimpleStreamExecutor

    @Test
    fun `cached one is evaluated once across multiple consumers`() {
        var evaluations = 0
        val cached = IStream.of(Unit).map {
            evaluations++
            42
        }.cached()

        val result = executor.query {
            cached.map { it + 1000 }.plus(cached.map { it + 2000 }).toList()
        }

        assertEquals(listOf(1042, 2042), result)
        assertEquals(1, evaluations) // the side effect ran exactly once, not once per consumer
    }

    @Test
    fun `without cached the side effect runs per consumer`() {
        var evaluations = 0
        val notCached = IStream.of(Unit).map {
            evaluations++
            42
        }

        val result = executor.query {
            notCached.map { it + 1000 }.plus(notCached.map { it + 2000 }).toList()
        }

        assertEquals(listOf(1042, 2042), result)
        assertEquals(2, evaluations) // confirms the distinction: each consumer re-evaluates
    }

    @Test
    fun `cached one shared via zip evaluates once`() {
        var evaluations = 0
        val cached = IStream.of(7).map {
            evaluations++
            it
        }.cached()

        val result = executor.query { cached.zipWith(cached) { a, b -> a + b } }

        assertEquals(14, result)
        assertEquals(1, evaluations)
    }
}
