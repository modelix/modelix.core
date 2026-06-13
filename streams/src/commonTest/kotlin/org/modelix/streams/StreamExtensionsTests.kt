package org.modelix.streams

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamExtensionsTests {

    private val executor = SimpleStreamExecutor

    @Test
    fun `distinct removes duplicates`() {
        assertEquals(
            listOf("g", "a", "d", "h", "z"),
            executor.query { IStream.many(listOf("g", "g", "a", "d", "h", "z", "g", "h")).distinct().toList() },
        )
    }

    @Test
    fun `map and filter`() {
        assertEquals(
            listOf(20, 40),
            executor.query { IStream.many(1..4).map { it * 10 }.filter { it % 20 == 0 }.toList() },
        )
    }

    @Test
    fun `flatMap concatenates in order`() {
        assertEquals(
            listOf(1, -1, 2, -2, 3, -3),
            executor.query { IStream.many(1..3).flatMapOrdered { IStream.many(listOf(it, -it)) }.toList() },
        )
    }

    @Test
    fun `zip combines single values`() {
        assertEquals(
            "a1",
            executor.query { IStream.of("a").zipWith(IStream.of(1)) { a, b -> "$a$b" } },
        )
    }

    @Test
    fun `fold and count`() {
        assertEquals(10, executor.query { IStream.many(1..4).fold(0) { acc, v -> acc + v } })
        assertEquals(4, executor.query { IStream.many(1..4).count() })
    }
}
