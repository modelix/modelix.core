package org.modelix.datastructures.hamt

import org.modelix.datastructures.MapChangeEvent
import org.modelix.datastructures.btree.BTree
import org.modelix.datastructures.btree.BTreeNodeLeaf
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.streams.IStream

class HamtCollisionNode<K, V : Any>(
    override val config: Config<K, V>,
    val btree: BTree<K, V>,
) : HamtNode<K, V>() {

    private fun newNode(newTree: BTree<K, V>): HamtNode<K, V>? {
        val newRoot = newTree.root
        if (btree.root === newRoot) return this
        return if (newRoot is BTreeNodeLeaf) {
            when (newRoot.entries.size) {
                0 -> null
                1 -> newRoot.entries.single().let { HamtLeafNode(config, it.key, it.value) }
                else -> HamtCollisionNode(config, newTree)
            }
        } else {
            HamtCollisionNode(config, newTree)
        }
    }

    override fun getAll(
        keys: Iterable<K>,
        shift: Int,
    ): IStream.Many<Pair<K, V?>> {
        return btree.getAll(keys)
    }

    override fun put(
        key: K,
        value: V,
        shift: Int,
        graph: IObjectGraph,
    ): IStream.One<HamtNode<K, V>> {
        return IStream.of(newNode(btree.put(key, value))!!)
    }

    override fun remove(
        key: K,
        shift: Int,
        graph: IObjectGraph,
    ): IStream.ZeroOrOne<HamtNode<K, V>> {
        return IStream.deferZeroOrOne {
            IStream.ofNotNull(newNode(btree.remove(key)))
        }
    }

    override fun get(key: K, shift: Int): IStream.ZeroOrOne<V> {
        return btree.getAll(listOf(key)).filter { it.first == key }.firstOrEmpty().map { it.second }
    }

    override fun putAll(
        entries: List<Pair<K, V>>,
        shift: Int,
        graph: IObjectGraph,
    ): IStream.One<HamtNode<K, V>> {
        var newTree = btree
        for ((key, value) in entries) {
            newTree = btree.put(key, value)
        }
        return IStream.of(newNode(newTree)!!)
    }

    override fun getEntries(): IStream.Many<Pair<K, V>> {
        return btree.getEntries().map { it.key to it.value }
    }

    override fun getChanges(
        oldNode: HamtNode<K, V>?,
        shift: Int,
        changesOnly: Boolean,
    ): IStream.Many<MapChangeEvent<K, V>> {
        TODO("Not yet implemented")
    }

    override fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
        shift: Int,
    ): IStream.Many<Object<*>> {
        TODO("Not yet implemented")
    }

    override fun serialize(): String {
        return "C" + SEPARATOR + btree.root.serialize()
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return btree.root.getContainmentReferences()
    }
}
