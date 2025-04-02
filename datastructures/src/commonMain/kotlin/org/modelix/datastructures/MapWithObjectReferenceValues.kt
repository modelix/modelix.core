package org.modelix.datastructures

import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider

class MapWithObjectReferenceValues<K, V : IObjectData>(
    val graph: IObjectGraph,
    val map: IPersistentMap<K, ObjectReference<V>>,
) : IPersistentMap<K, V>, IStreamExecutorProvider by graph {

    private fun IPersistentMap<K, ObjectReference<V>>.wrap() = MapWithObjectReferenceValues(graph, this)
    private fun IStream.One<IPersistentMap<K, ObjectReference<V>>>.wrap() = map { it.wrap() }
    private fun V.toRef() = graph.fromCreated(this)

    override fun asObject(): Object<*> {
        return map.asObject()
    }

    override fun getKeyTypeConfig(): IDataTypeConfiguration<K> {
        return map.getKeyTypeConfig()
    }

    override fun putAll(entries: Iterable<Pair<K, V>>): IStream.One<MapWithObjectReferenceValues<K, V>> {
        return map.putAll(entries.map { it.first to it.second.toRef() }).wrap()
    }

    override fun removeAll(keys: Iterable<K>): IStream.One<MapWithObjectReferenceValues<K, V>> {
        return map.removeAll(keys).wrap()
    }

    override fun removeAllEntries(entries: Iterable<Pair<K, V>>): IStream.One<MapWithObjectReferenceValues<K, V>> {
        return map.removeAllEntries(entries.map { it.first to it.second.toRef() }).wrap()
    }

    override fun getAllValues(keys: Iterable<K>): IStream.Many<V> {
        return map.getAllValues(keys).flatMap { it.resolve() }.map { it.data }
    }

    override fun getAllValues(): IStream.Many<V> {
        return map.getAllValues().flatMap { it.resolve() }.map { it.data }
    }

    override fun getAll(keys: Iterable<K>): IStream.Many<Pair<K, V>> {
        return map.getAll(keys).flatMap { entry -> entry.second.resolve().map { value -> entry.first to value.data } }
    }

    override fun getAll(): IStream.Many<Pair<K, V>> {
        return map.getAll().flatMap { entry -> entry.second.resolve().map { value -> entry.first to value.data } }
    }

    override fun put(key: K, value: V) = putAll(listOf(key to value))

    override fun remove(key: K) = removeAll(listOf(key))

    override fun getChanges(
        oldMap: IPersistentMap<K, V>,
        changesOnly: Boolean,
    ): IStream.Many<MapChangeEvent<K, V>> {
        oldMap as MapWithObjectReferenceValues<K, V>
        return map.getChanges(oldMap.map, changesOnly).flatMap { event ->
            when (event) {
                is EntryAddedEvent<K, ObjectReference<V>> -> {
                    event.value.resolveData().map { value ->
                        EntryAddedEvent(event.key, value)
                    }
                }
                is EntryChangedEvent<K, ObjectReference<V>> -> {
                    event.oldValue.resolveData().zipWith(event.newValue.resolveData()) { oldValue, newValue ->
                        EntryChangedEvent(event.key, oldValue, newValue)
                    }
                }
                is EntryRemovedEvent<K, ObjectReference<V>> -> {
                    event.value.resolveData().map { value ->
                        EntryRemovedEvent(event.key, value)
                    }
                }
            }
        }
    }
}

fun <K, V : IObjectData> IPersistentMap<K, ObjectReference<V>>.autoResolveValues(): IPersistentMap<K, V> {
    return MapWithObjectReferenceValues(asObject().graph, this)
}
