package org.modelix.datastructures.btree

data class RemovedEntry<K, V>(val entry: BTreeEntry<K, V>, val updatedNode: Replacement<K, V>) {
    fun splitIfNecessary(): RemovedEntry<K, V> {
        return copy(updatedNode = updatedNode.splitIfNecessary())
    }
}
