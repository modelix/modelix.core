package org.modelix.datastructures.btree

import org.modelix.datastructures.objects.Base64DataTypeConfiguration
import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.LongDataTypeConfiguration
import org.modelix.datastructures.objects.StringDataTypeConfiguration

data class BTreeConfig<K, V>(
    val minEntries: Int,
    val minChildren: Int,
    val multimap: Boolean,
    val keyConfiguration: IDataTypeConfiguration<K>,
    val valueConfiguration: IDataTypeConfiguration<V>,
    val graph: IObjectGraph,
) {
    val maxEntries = 2 * minEntries
    val maxChildren = 2 * minChildren
    val nodeDeserializer = BTreeNode.Deserializer(this)
    val entryComparatorForInsertion: Comparator<BTreeEntry<K, V>> = if (multimap) {
        compareBy<BTreeEntry<K, V>, K>(keyConfiguration) { it.key }
            .thenBy<BTreeEntry<K, V>, V>(valueConfiguration) { it.value }
    } else {
        compareBy<BTreeEntry<K, V>, K>(keyConfiguration) { it.key }
    }

    companion object {
        fun builder() = BTreeConfigBuilder<Nothing, Nothing>()
    }
}

class BTreeConfigBuilder<K, V> {
    private var graph: IObjectGraph? = null
    private var minEntries: Int = 8
    private var minChildren: Int = 8
    private var keyConfiguration: IDataTypeConfiguration<K>? = null
    private var valueConfiguration: IDataTypeConfiguration<V>? = null
    private var multimap: Boolean = true

    fun minEntries(value: Int) = also { minEntries = value }
    fun minChildren(value: Int) = also { minChildren = value }

    fun maxEntries(value: Int) = also {
        require(value % 2 == 0) { "Has to be a multiple of 2: $value" }
        minEntries(value / 2)
    }

    fun maxChildren(value: Int) = also {
        require(value % 2 == 0) { "Has to be a multiple of 2: $value" }
        minChildren(value / 2)
    }

    fun graph(value: IObjectGraph) = also { graph = value }

    fun <T> keyConfiguration(config: IDataTypeConfiguration<T>): BTreeConfigBuilder<T, V> {
        return (this as BTreeConfigBuilder<T, V>).also { it.keyConfiguration = config }
    }

    fun <T> valueConfiguration(config: IDataTypeConfiguration<T>): BTreeConfigBuilder<K, T> {
        return (this as BTreeConfigBuilder<K, T>).also { it.valueConfiguration = config }
    }

    fun multipleValuesPerKey() = also { multimap = true }
    fun singleValuePerKey() = also { multimap = false }

    fun stringKeys() = keyConfiguration(Base64DataTypeConfiguration(StringDataTypeConfiguration()))
    fun stringValues() = valueConfiguration(Base64DataTypeConfiguration(StringDataTypeConfiguration()))
    fun longKeys() = keyConfiguration(LongDataTypeConfiguration())
    fun longValues() = valueConfiguration(LongDataTypeConfiguration())

    fun build() = BTreeConfig(
        graph = checkNotNull(graph) { "graph not specified" },
        minEntries = minEntries,
        minChildren = minChildren,
        multimap = multimap,
        keyConfiguration = checkNotNull(keyConfiguration) { "keyConfiguration not specified" },
        valueConfiguration = checkNotNull(valueConfiguration) { "valueConfiguration not specified" },
    )
}
