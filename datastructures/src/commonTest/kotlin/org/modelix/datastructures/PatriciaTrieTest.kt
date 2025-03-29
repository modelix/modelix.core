package org.modelix.datastructures

import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.patricia.PatriciaTrie
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class PatriciaTrieTest {

    @Test
    fun t() {
        val nodeIds = listOf(
            "mps:34cea670-b8fe-4267-a02b-fb909cbaed73/r:f215f833-19b2-45fc-9e67-5529135b4321/50355284",
            "mps:34cea670-b8fe-4267-a02b-fb909cbaed73/r:f215f833-19b2-45fc-9e67-5529135b4321/18438666",
            "mps:34cea670-b8fe-4267-a02b-fb909cbaed73/r:a59f48ad-7ba8-4416-8c08-47a9de87b215/62364071",
            "mps:34cea670-b8fe-4267-a02b-fb909cbaed73/r:a59f48ad-7ba8-4416-8c08-47a9de87b215",
            "mps:34cea670-b8fe-4267-a02b-fb909cbaed73/r:a59f48ad-7ba8-4416-8c08-47a9de87b215/27454692",
            "mps:50b60877-0622-4237-aefa-34e7398b7a85/r:4be074c1-e97e-49ca-993e-fa98633f4af9/22431072",
            "mps:50b60877-0622-4237-aefa-34e7398b7a85/r:4be074c1-e97e-49ca-993e-fa98633f4af9/60219668",
            "mps:50b60877-0622-4237-aefa-34e7398b7a85/r:4be074c1-e97e-49ca-993e-fa98633f4af9/51334973",
            "mps:50b60877-0622-4237-aefa-34e7398b7a85/r:4be074c1-e97e-49ca-993e-fa98633f4af9/52699726",
            "mps:50b60877-0622-4237-aefa-34e7398b7a85/r:5e9fda19-032b-4c60-85b8-4104775f7e0d/28517075",
            "mps:50b60877-0622-4237-aefa-34e7398b7a85/r:5e9fda19-032b-4c60-85b8-4104775f7e0d/13232611",
        )

        var tree = PatriciaTrie.withStrings(IObjectGraph.FREE_FLOATING)
        println(tree)

        for (id in nodeIds.drop(1)) {
            tree = tree.put(id, id).getSynchronous()
            println(tree)
        }

        println(tree)

        println(tree.slice("mps:50b"))
    }

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
}
