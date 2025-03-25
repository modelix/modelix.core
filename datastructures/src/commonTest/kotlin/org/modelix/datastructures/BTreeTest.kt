package org.modelix.datastructures

import org.modelix.datastructures.btree.BTree
import org.modelix.datastructures.btree.BTreeConfig
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.streams.SequenceStreamBuilder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class BTreeTest {

    @Test
    fun `can insert node`() = runTest(1_000, 10_000, 5)

    fun runTest(numOperations: Int, keyRange: Int, valueRange: Int) {
        val rand = Random(6734687)
        var tree = BTree<String, String>(
            BTreeConfig.builder()
                .graph(IObjectGraph.FREE_FLOATING).stringKeys().stringValues().build(),
        )

        val expected = HashMap<String, String>()

        repeat(numOperations) {
            when (rand.nextInt(5)) {
                0 -> {
                    if (expected.isNotEmpty()) {
                        val key = expected.keys.random(rand)
                        // println("remove $key")
                        tree = tree.remove(key)
                        expected.remove(key)
                    }
                }
                else -> {
                    val key = "k" + rand.nextInt(keyRange).toString()
                    val value = "v" + rand.nextInt(valueRange).toString()
                    // println("insert $key -> $value")
                    tree = tree.put(key, value)
                    expected[key] = value
                }
            }
            tree.validate()

            for (entry in expected.entries) {
                assertEquals(entry.value, tree.get(entry.key), "for key ${entry.key}")
            }
        }
    }

    @Test
    fun `can clear all entries`() {
        var tree = BTree(BTreeConfig.builder().graph(IObjectGraph.FREE_FLOATING).longKeys().longValues().build())

        for (i in 1L..1000L) {
            tree = tree.put(i, i)
        }
        for (i in 1L..1000L) {
            tree = tree.remove(i)
        }

        assertEquals(emptyList(), tree.graph.getStreamExecutor().query { tree.getEntries().toList() })
    }

    @Test
    fun `can bulk request entries`() {
        var tree = BTree(BTreeConfig.builder().graph(IObjectGraph.FREE_FLOATING).longKeys().longValues().build())

        for (i in 1L..1000L) {
            tree = tree.put(i, i * 2)
        }

        assertEquals(
            (100L..200L).map { it to it * 2 },
            SequenceStreamBuilder.INSTANCE.getStreamExecutor().query {
                tree.getAll((100L..200L)).toList()
            },
        )
    }
}
