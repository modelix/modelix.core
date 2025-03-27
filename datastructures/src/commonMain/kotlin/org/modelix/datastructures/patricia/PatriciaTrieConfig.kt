package org.modelix.datastructures.patricia

import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectGraph

class PatriciaTrieConfig<K, V>(
    val graph: IObjectGraph,
    val keyConfig: IDataTypeConfiguration<K>,
    val valueConfig: IDataTypeConfiguration<V>,
)
