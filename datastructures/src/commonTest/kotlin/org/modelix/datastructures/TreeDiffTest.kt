package org.modelix.datastructures

import org.modelix.datastructures.objects.FullyLoadedObjectGraph
import org.modelix.datastructures.objects.StringDataTypeConfiguration
import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.datastructures.patricia.PatriciaNode
import org.modelix.datastructures.patricia.PatriciaTrie
import org.modelix.datastructures.patricia.PatriciaTrieConfig
import org.modelix.streams.getBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class TreeDiffTest {

    @Test
    fun `tree can be restored from diff`() {
        val graph = FullyLoadedObjectGraph()
        var tree = PatriciaTrie.withStrings(graph)

        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        val rand = Random(5465)
        fun randomString(length: Int) = (0 until length).joinToString("") { alphabet.random(rand).toString() }

        val removedEntries = (1..100).map { randomString(rand.nextInt(0, 6)) }.toMutableSet()
        val values = removedEntries.associateWith { "value_of_$it" }
        val addedEntries = mutableSetOf<String>()

        val restoringGraph = FullyLoadedObjectGraph()
        val initialDiff = tree.asObject().getDescendantsAndSelf()
            .map { it.getHash() to it.data.serialize() }
            .toList().getBlocking(tree).toMap()
        val restoringConfig = PatriciaTrieConfig(
            restoringGraph,
            StringDataTypeConfiguration(),
            StringDataTypeConfiguration(),
        )
        val restoringDeserializer = PatriciaNode.Deserializer { restoringConfig }
        var restoredTree = PatriciaTrie(restoringGraph.loadObjects(tree.asObject().getHash(), restoringDeserializer, initialDiff))
        fun assertTree() {
            val diff = tree.asObject().objectDiff(restoredTree.asObject())
                .map { it.getHash() to it.data.serialize() }
                .toList().getBlocking(tree).toMap()
            restoredTree = PatriciaTrie(restoringGraph.loadObjects(tree.asObject().getHash(), restoringDeserializer, diff))

            val expectedEntries = addedEntries.associateWith { values[it]!! }
            val actualEntries = restoredTree.getAll().toList().getBlocking(tree).toMap()
            assertEquals(expectedEntries, actualEntries)
        }

        repeat(1_000) {
            if (removedEntries.size > addedEntries.size) {
                val key = removedEntries.random(rand)
                removedEntries.remove(key)
                addedEntries.add(key)
                tree = tree.put(key, values[key]!!).getBlocking(tree)
                assertTree()
            } else {
                val key = addedEntries.random(rand)
                removedEntries.add(key)
                addedEntries.remove(key)
                tree = tree.remove(key).getBlocking(tree)
                assertTree()
            }
        }

        assertTree()
    }
}
