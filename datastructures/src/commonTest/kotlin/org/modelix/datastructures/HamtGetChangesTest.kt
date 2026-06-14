package org.modelix.datastructures

import org.modelix.datastructures.hamt.HamtInternalNode
import org.modelix.datastructures.hamt.HamtNode
import org.modelix.datastructures.hamt.HamtTree
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.StringDataTypeConfiguration
import org.modelix.streams.getBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Direct coverage of [org.modelix.datastructures.IPersistentMap.getChanges] on the HAMT. Guards that a value change is
 * reported as [EntryChangedEvent] (not add/remove): this relies on the streams engine evaluating a synchronous prefix
 * eagerly, which `HamtLeafNode.getChanges` depends on via a `var` set by one stream and read by a later deferred one.
 */
class HamtGetChangesTest {
    private fun newTree(): HamtTree<String, String> {
        val config = HamtNode.Config(
            graph = IObjectGraph.FREE_FLOATING,
            keyConfig = StringDataTypeConfiguration(),
            valueConfig = StringDataTypeConfiguration(),
        )
        return HamtTree(HamtInternalNode.createEmpty(config))
    }

    @Test
    fun single_entry_value_change_is_a_change_not_add() {
        val old = newTree().put("k1", "a").getBlocking()
        val new = old.put("k1", "b").getBlocking()
        val changes = new.getChanges(old, changesOnly = false).toList().getBlocking()
        assertEquals(listOf(EntryChangedEvent("k1", "a", "b")), changes)
    }

    @Test
    fun one_changed_among_several() {
        var old = newTree()
        for (i in 0 until 20) old = old.put("k$i", "v$i").getBlocking()
        val new = old.put("k7", "changed").getBlocking()
        val changes = new.getChanges(old, changesOnly = false).toList().getBlocking()
        assertEquals(listOf(EntryChangedEvent("k7", "v7", "changed")), changes)
    }
}
