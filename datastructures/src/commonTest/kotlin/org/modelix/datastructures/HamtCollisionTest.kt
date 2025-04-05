package org.modelix.datastructures

import org.modelix.datastructures.hamt.HamtInternalNode
import org.modelix.datastructures.hamt.HamtNode
import org.modelix.datastructures.hamt.HamtTree
import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.LongDataTypeConfiguration
import org.modelix.datastructures.objects.StringDataTypeConfiguration
import org.modelix.streams.getBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class HamtCollisionTest {

    @Test
    fun `can store two entries with the same hash`() {
        val graph = IObjectGraph.FREE_FLOATING

        val keyType = DataTypeWithHashCollisions(0b100000001, LongDataTypeConfiguration())
        val valueType = StringDataTypeConfiguration()

        val config = HamtNode.Config(
            graph = graph,
            keyConfig = keyType,
            valueConfig = valueType,
        )
        var tree: HamtTree<Long, String> = HamtTree(HamtInternalNode.createEmpty(config))

        tree = tree.put(0b000000000, "a").getBlocking(tree)
        tree = tree.put(0b000000001, "b").getBlocking(tree)
        tree = tree.put(0b000000011, "c").getBlocking(tree)
        tree = tree.put(0b100000000, "d").getBlocking(tree)
        tree = tree.put(0b100000001, "e").getBlocking(tree)
        tree = tree.put(0b100000011, "f").getBlocking(tree)

        assertEquals("a", tree.get(0b000000000).getBlocking(tree))
        assertEquals("b", tree.get(0b000000001).getBlocking(tree))
        assertEquals("c", tree.get(0b000000011).getBlocking(tree))
        assertEquals("d", tree.get(0b100000000).getBlocking(tree))
        assertEquals("e", tree.get(0b100000001).getBlocking(tree))
        assertEquals("f", tree.get(0b100000011).getBlocking(tree))
    }

    /**
     * BTrees can be configured as a multimap. This test ensures that entries with the same key are overwritten
     * instead of added.
     */
    @Test
    fun `override entries in btree`() {
        val graph = IObjectGraph.FREE_FLOATING

        val keyType = DataTypeWithHashCollisions(0b1, LongDataTypeConfiguration())
        val valueType = StringDataTypeConfiguration()

        val config = HamtNode.Config(
            graph = graph,
            keyConfig = keyType,
            valueConfig = valueType,
        )
        var tree: HamtTree<Long, String> = HamtTree(HamtInternalNode.createEmpty(config))

        tree = tree.put(0b00, "a").getBlocking(tree)
        tree = tree.put(0b01, "b").getBlocking(tree)
        tree = tree.put(0b10, "c").getBlocking(tree)
        tree = tree.put(0b11, "d").getBlocking(tree)

        assertEquals("a", tree.get(0b00).getBlocking(tree))
        assertEquals("b", tree.get(0b01).getBlocking(tree))
        assertEquals("c", tree.get(0b10).getBlocking(tree))
        assertEquals("d", tree.get(0b11).getBlocking(tree))

        tree = tree.put(0b01, "changed").getBlocking(tree)

        assertEquals("a", tree.get(0b00).getBlocking(tree))
        assertEquals("changed", tree.get(0b01).getBlocking(tree))
        assertEquals("c", tree.get(0b10).getBlocking(tree))
        assertEquals("d", tree.get(0b11).getBlocking(tree))
    }
}

private class DataTypeWithHashCollisions<E>(val mask: Long, val wrapped: IDataTypeConfiguration<E>) : IDataTypeConfiguration<E> by wrapped {
    override fun hashCode64(element: E): Long {
        return wrapped.hashCode64(element) and mask
    }

    override fun hashCode32(element: E): Int {
        return (wrapped.hashCode32(element).toLong() and mask).toInt()
    }
}
