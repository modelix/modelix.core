package org.modelix.datastructures.patricia

import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectGraph
import kotlin.jvm.JvmName

class PatriciaTrieConfig<K, V>(
    val graph: IObjectGraph,
    val keyConfig: IDataTypeConfiguration<K>,
    val valueConfig: IDataTypeConfiguration<V>,
) {

    @JvmName("keysEqual")
    fun equal(a: K, b: K): Boolean {
        return if (a == null) {
            b == null
        } else {
            b != null && keyConfig.equal(a, b)
        }
    }

    @JvmName("valuesEqual")
    fun equal(a: V?, b: V?): Boolean {
        return if (a == null) {
            b == null
        } else {
            b != null && valueConfig.equal(a, b)
        }
    }
}
