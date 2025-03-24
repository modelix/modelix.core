package org.modelix.datastructures

import org.modelix.datastructures.hamt.HamtInternalNode
import org.modelix.datastructures.hamt.HamtNode
import org.modelix.datastructures.hamt.HamtTree
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.StringDataTypeConfiguration
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class HamtTest {

    @Test
    fun `can insert node`() = runTest(1_000, 10_000, 5)

    fun runTest(numOperations: Int, keyRange: Int, valueRange: Int) {
        val rand = Random(6734687)
        val graph = IObjectGraph.FREE_FLOATING
        val config = HamtNode.Config(
            graph = graph,
            keyConfig = StringDataTypeConfiguration(),
            valueConfig = StringDataTypeConfiguration(),
        )
        var tree: HamtTree<String, String> = HamtTree(HamtInternalNode.createEmpty(config))

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

            for (entry in expected.entries) {
                assertEquals(entry.value, tree.get(entry.key), "for key ${entry.key}")
            }
        }
    }
}
