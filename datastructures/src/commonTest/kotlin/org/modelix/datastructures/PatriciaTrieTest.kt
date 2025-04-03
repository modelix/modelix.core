package org.modelix.datastructures

import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.patricia.PatriciaTrie
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class PatriciaTrieTest {

    @Test
    fun `same content produces same tree`() {
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        val rand = Random(5465)
        fun randomString(length: Int) = (0 until length).joinToString("") { alphabet.random().toString() }

        val initialTree = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)

        val removedEntries = (1..100).map { randomString(rand.nextInt(0, 6)) }.toMutableSet()
        val values = removedEntries.associateWith { "value_of_$it" }
        val addedEntries = mutableSetOf<String>()

        var tree = initialTree

        fun assertTree() {
            val expectedEntries = addedEntries.associateWith { values[it]!! }
            val actualEntries = tree.getAll().toList().getSynchronous().toMap()
            assertEquals(expectedEntries, actualEntries)

            val expectedTree = addedEntries.fold(initialTree) { acc, it -> acc.put(it, values[it]!!).getSynchronous() }
            assertEquals(expectedTree, tree)

            assertEquals(expectedTree.asObject().getHash(), tree.asObject().getHash())
        }

        repeat(1_000) {
            if (removedEntries.size > addedEntries.size) {
                val key = removedEntries.random()
                removedEntries.remove(key)
                addedEntries.add(key)
                tree = tree.put(key, values[key]!!).getSynchronous()
                assertTree()
            } else {
                val key = addedEntries.random()
                removedEntries.add(key)
                addedEntries.remove(key)
                tree = tree.remove(key).getSynchronous()
                assertTree()
            }
        }

        assertTree()
    }

    @Test
    fun `slice with shorter prefix and single entry`() {
        var tree: PatriciaTrie<String, String> = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)
        tree = tree.put("abcdef", "1").getSynchronous()

        assertEquals("1", tree.slice("abc").flatMapZeroOrOne { it.get("abcdef") }.getSynchronous())
    }

    @Test
    fun `slice with shorter prefix and two entries`() {
        var tree: PatriciaTrie<String, String> = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)
        tree = tree.put("abcdef", "1").getSynchronous()
        tree = tree.put("abcdeg", "2").getSynchronous()

        assertEquals("1", tree.slice("abc").flatMapZeroOrOne { it.get("abcdef") }.getSynchronous())
        assertEquals("2", tree.slice("abc").flatMapZeroOrOne { it.get("abcdeg") }.getSynchronous())
    }

    @Test
    fun `slice with prefix between two entries`() {
        var tree: PatriciaTrie<String, String> = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)
        tree = tree.put("ab", "1").getSynchronous()
        tree = tree.put("abcdef", "2").getSynchronous()

        assertEquals(null, tree.slice("abcd").flatMapZeroOrOne { it.get("ab") }.getSynchronous())
        assertEquals("2", tree.slice("abcd").flatMapZeroOrOne { it.get("abcdef") }.getSynchronous())
    }

    @Test
    fun `slice with one before and two after`() {
        var tree: PatriciaTrie<String, String> = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)
        tree = tree.put("ab", "1").getSynchronous()
        tree = tree.put("abcdef", "2").getSynchronous()
        tree = tree.put("abcdeg", "3").getSynchronous()

        assertEquals(null, tree.slice("abcd").flatMapZeroOrOne { it.get("ab") }.getSynchronous())
        assertEquals("2", tree.slice("abcd").flatMapZeroOrOne { it.get("abcdef") }.getSynchronous())
        assertEquals("3", tree.slice("abcd").flatMapZeroOrOne { it.get("abcdeg") }.getSynchronous())
    }

    @Test
    fun `slice with two before and two after at existing split`() {
        var tree: PatriciaTrie<String, String> = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)
        tree = tree.put("a", "0").getSynchronous()
        tree = tree.put("ab", "1").getSynchronous()
        tree = tree.put("abcdef", "2").getSynchronous()
        tree = tree.put("abcdeg", "3").getSynchronous()

        assertEquals(null, tree.slice("ab").flatMapZeroOrOne { it.get("a") }.getSynchronous())
        assertEquals("1", tree.slice("ab").flatMapZeroOrOne { it.get("ab") }.getSynchronous())
        assertEquals("2", tree.slice("ab").flatMapZeroOrOne { it.get("abcdef") }.getSynchronous())
        assertEquals("3", tree.slice("ab").flatMapZeroOrOne { it.get("abcdeg") }.getSynchronous())
    }

    @Test
    fun `can replace subtree`() {
        var tree: PatriciaTrie<String, String> = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)

        val initialKeys = listOf(
            "a",
            "ab",
            "abc",
            "abcd",
            "abcdA",
            "abcdB1",
            "abcdB11",
            "abcdB2",
            "abcdB21",
            "abcdB22",
            "abcdB23",
            "abcdB3",
            "abcdC",
        )
        for (key in initialKeys) {
            tree = tree.put(key, "value of $key").getSynchronous()
        }

        assertEquals(
            initialKeys.associateWith { "value of $it" },
            tree.getAll().toList().getSynchronous().toMap(),
        )

        for (key in initialKeys) {
            assertEquals("value of $key", tree.get(key).getSynchronous())
        }

        var replacementTree = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)
        val replacementKeys = listOf(
            "abcdB1",
            "abcdB22",
            "abcdB81",
            "abcdB911",
            "abcdB92",
            "abcdB921",
            "abcdB922",
            "abcdB93",
        )
        for (key in replacementKeys) {
            replacementTree = replacementTree.put(key, "new value of $key").getSynchronous()
        }
        tree = tree.replaceSlice("abcdB", replacementTree).getSynchronous()

        assertEquals(
            initialKeys.filterNot { it.startsWith("abcdB") }.map { it to "value of $it" }
                .plus(replacementKeys.map { it to "new value of $it" })
                .sortedBy { it.first }
                .joinToString("\n") { "${it.first} -> ${it.second}" },
            tree.getAll().toList().getSynchronous().sortedBy { it.first }.joinToString("\n") { "${it.first} -> ${it.second}" },
        )
    }
}
