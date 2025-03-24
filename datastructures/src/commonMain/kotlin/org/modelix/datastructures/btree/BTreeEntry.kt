package org.modelix.datastructures.btree

class BTreeEntry<K, V>(val key: K, val value: V) {
    override fun toString(): String {
        return "$key -> $value"
    }
}
