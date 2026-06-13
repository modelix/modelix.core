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

    @Test
    fun `any all none`() {
        assertEquals(true, executor.query { IStream.many(1..4).any { it > 3 } })
        assertEquals(false, executor.query { IStream.many(1..4).any { it > 4 } })
        assertEquals(true, executor.query { IStream.many(1..4).all { it > 0 } })
        assertEquals(false, executor.query { IStream.many(1..4).all { it > 1 } })
        assertEquals(true, executor.query { IStream.many(1..4).none { it > 4 } })
        assertEquals(true, executor.query { IStream.many(emptyList<Int>()).all { it > 0 } })
        assertEquals(true, executor.query { IStream.many(emptyList<Int>()).none() })
    }

    @Test
    fun `filterNot and filterIsInstance`() {
        assertEquals(listOf(1, 3), executor.query { IStream.many(1..4).filterNot { it % 2 == 0 }.toList() })
        assertEquals(
            listOf("a", "b"),
            executor.query { IStream.many(listOf<Any>("a", 1, "b", 2)).filterIsInstance<String>().toList() },
        )
    }

    @Test
    fun `first and last`() {
        assertEquals(1, executor.query { IStream.many(1..4).first() })
        assertEquals(2, executor.query { IStream.many(1..4).first { it % 2 == 0 } })
        assertEquals(null, executor.query { IStream.many(1..4).firstOrNull { it > 4 } })
        assertEquals(4, executor.query { IStream.many(1..4).last() })
        assertEquals(null, executor.query { IStream.many(emptyList<Int>()).lastOrNull() })
    }

    @Test
    fun `mapIndexed and filterIndexed`() {
        assertEquals(
            listOf("0:a", "1:b"),
            executor.query { IStream.many(listOf("a", "b")).mapIndexed { i, v -> "$i:$v" }.toList() },
        )
        assertEquals(
            listOf("a", "c"),
            executor.query { IStream.many(listOf("a", "b", "c", "d")).filterIndexed { i, _ -> i % 2 == 0 }.toList() },
        )
    }

    @Test
    fun `collection conversions`() {
        assertEquals(setOf(1, 2, 3), executor.query { IStream.many(listOf(1, 2, 2, 3)).toSet() })
        assertEquals(
            mapOf(0 to listOf(2, 4), 1 to listOf(1, 3)),
            executor.query { IStream.many(1..4).groupBy { it % 2 } },
        )
        assertEquals(
            mapOf(1 to "a", 2 to "bb"),
            executor.query { IStream.many(listOf("a", "bb")).associateBy { it.length } },
        )
        assertEquals(
            mapOf("a" to 1, "bb" to 2),
            executor.query { IStream.many(listOf("a", "bb")).associateWith { it.length } },
        )
        assertEquals(
            mapOf(1 to "a", 2 to "b"),
            executor.query { IStream.many(listOf(1 to "a", 2 to "b")).toMap() },
        )
    }

    @Test
    fun `sorting and distinctBy`() {
        assertEquals(listOf(1, 2, 3, 4), executor.query { IStream.many(listOf(3, 1, 4, 2)).sorted().toList() })
        assertEquals(listOf(4, 3, 2, 1), executor.query { IStream.many(listOf(3, 1, 4, 2)).sortedDescending().toList() })
        assertEquals(
            listOf("a", "bb", "ccc"),
            executor.query { IStream.many(listOf("ccc", "a", "bb")).sortedBy { it.length }.toList() },
        )
        assertEquals(
            listOf("a", "bb"),
            executor.query { IStream.many(listOf("a", "bb", "cc", "d")).distinctBy { it.length }.toList() },
        )
    }

    @Test
    fun `reductions`() {
        assertEquals(24, executor.query { IStream.many(1..4).reduce { acc, v -> acc * v } })
        assertEquals(10, executor.query { IStream.many(1..4).sumOf { it } })
        assertEquals(10, executor.query { IStream.many(1..4).sum() })
        assertEquals(10L, executor.query { IStream.many(listOf(1L, 2L, 3L, 4L)).sum() })
        assertEquals("ccc", executor.query { IStream.many(listOf("a", "bb", "ccc")).maxByOrNull { it.length } })
        assertEquals("a", executor.query { IStream.many(listOf("a", "bb", "ccc")).minByOrNull { it.length } })
        assertEquals(null, executor.query { IStream.many(emptyList<String>()).maxByOrNull { it.length } })
    }

    @Test
    fun `startWith endWith and joinToString`() {
        assertEquals(listOf(0, 1, 2, 3), executor.query { IStream.many(1..3).startWith(0).toList() })
        assertEquals(listOf(1, 2, 3, 4), executor.query { IStream.many(1..3).endWith(4).toList() })
        assertEquals("[1, 2, 3]", executor.query { IStream.many(1..3).joinToString(prefix = "[", postfix = "]") })
    }
}
