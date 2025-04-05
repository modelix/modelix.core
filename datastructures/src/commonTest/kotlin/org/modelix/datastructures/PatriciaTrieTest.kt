package org.modelix.datastructures

import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.patricia.PatriciaTrie
import org.modelix.streams.getBlocking
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
            val actualEntries = tree.getAll().toList().getBlocking(tree).toMap()
            assertEquals(expectedEntries, actualEntries)

            val expectedTree = addedEntries.fold(initialTree) { acc, it -> acc.put(it, values[it]!!).getBlocking(tree) }
            assertEquals(expectedTree, tree)

            assertEquals(expectedTree.asObject().getHash(), tree.asObject().getHash())
        }

        repeat(1_000) {
            if (removedEntries.size > addedEntries.size) {
                val key = removedEntries.random()
                removedEntries.remove(key)
                addedEntries.add(key)
                tree = tree.put(key, values[key]!!).getBlocking(tree)
                assertTree()
            } else {
                val key = addedEntries.random()
                removedEntries.add(key)
                addedEntries.remove(key)
                tree = tree.remove(key).getBlocking(tree)
                assertTree()
            }
        }

        assertTree()
    }

    @Test
    fun `slice with shorter prefix and single entry`() {
        var tree: PatriciaTrie<String, String> = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)
        tree = tree.put("abcdef", "1").getBlocking(tree)

        assertEquals("1", tree.slice("abc").flatMapZeroOrOne { it.get("abcdef") }.getBlocking(tree))
    }

    @Test
    fun `slice with shorter prefix and two entries`() {
        var tree: PatriciaTrie<String, String> = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)
        tree = tree.put("abcdef", "1").getBlocking(tree)
        tree = tree.put("abcdeg", "2").getBlocking(tree)

        assertEquals("1", tree.slice("abc").flatMapZeroOrOne { it.get("abcdef") }.getBlocking(tree))
        assertEquals("2", tree.slice("abc").flatMapZeroOrOne { it.get("abcdeg") }.getBlocking(tree))
    }

    @Test
    fun `slice with prefix between two entries`() {
        var tree: PatriciaTrie<String, String> = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)
        tree = tree.put("ab", "1").getBlocking(tree)
        tree = tree.put("abcdef", "2").getBlocking(tree)

        assertEquals(null, tree.slice("abcd").flatMapZeroOrOne { it.get("ab") }.getBlocking(tree))
        assertEquals("2", tree.slice("abcd").flatMapZeroOrOne { it.get("abcdef") }.getBlocking(tree))
    }

    @Test
    fun `slice with one before and two after`() {
        var tree: PatriciaTrie<String, String> = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)
        tree = tree.put("ab", "1").getBlocking(tree)
        tree = tree.put("abcdef", "2").getBlocking(tree)
        tree = tree.put("abcdeg", "3").getBlocking(tree)

        assertEquals(null, tree.slice("abcd").flatMapZeroOrOne { it.get("ab") }.getBlocking(tree))
        assertEquals("2", tree.slice("abcd").flatMapZeroOrOne { it.get("abcdef") }.getBlocking(tree))
        assertEquals("3", tree.slice("abcd").flatMapZeroOrOne { it.get("abcdeg") }.getBlocking(tree))
    }

    @Test
    fun `slice with two before and two after at existing split`() {
        var tree: PatriciaTrie<String, String> = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)
        tree = tree.put("a", "0").getBlocking(tree)
        tree = tree.put("ab", "1").getBlocking(tree)
        tree = tree.put("abcdef", "2").getBlocking(tree)
        tree = tree.put("abcdeg", "3").getBlocking(tree)

        assertEquals(null, tree.slice("ab").flatMapZeroOrOne { it.get("a") }.getBlocking(tree))
        assertEquals("1", tree.slice("ab").flatMapZeroOrOne { it.get("ab") }.getBlocking(tree))
        assertEquals("2", tree.slice("ab").flatMapZeroOrOne { it.get("abcdef") }.getBlocking(tree))
        assertEquals("3", tree.slice("ab").flatMapZeroOrOne { it.get("abcdeg") }.getBlocking(tree))
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
            tree = tree.put(key, "value of $key").getBlocking(tree)
        }

        assertEquals(
            initialKeys.associateWith { "value of $it" },
            tree.getAll().toList().getBlocking(tree).toMap(),
        )

        for (key in initialKeys) {
            assertEquals("value of $key", tree.get(key).getBlocking(tree))
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
            replacementTree = replacementTree.put(key, "new value of $key").getBlocking(tree)
        }
        tree = tree.replaceSlice("abcdB", replacementTree).getBlocking(tree)

        assertEquals(
            initialKeys.filterNot { it.startsWith("abcdB") }.map { it to "value of $it" }
                .plus(replacementKeys.map { it to "new value of $it" })
                .sortedBy { it.first }
                .joinToString("\n") { "${it.first} -> ${it.second}" },
            tree.getAll().toList().getBlocking(tree).sortedBy { it.first }.joinToString("\n") { "${it.first} -> ${it.second}" },
        )
    }
}
