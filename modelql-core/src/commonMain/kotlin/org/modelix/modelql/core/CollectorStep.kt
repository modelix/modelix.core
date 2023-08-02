package org.modelix.modelql.core

import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

abstract class CollectorStep<E, CollectionT>() : AggregationStep<E, CollectionT>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<CollectionT>> {
        val element = getProducers().first().getOutputSerializer(serializersModule)
        return getOutputSerializer(element.upcast())
    }

    protected abstract fun getOutputSerializer(elementSerializer: KSerializer<IStepOutput<E>>): KSerializer<out IStepOutput<CollectionT>>
}

class CollectorStepOutput<E, InternalCollectionT, CollectionT>(
    val input: List<IStepOutput<E>>,
    val internalCollection: InternalCollectionT,
    val output: CollectionT
) : IStepOutput<CollectionT> {
    override val value: CollectionT get() = output
}

abstract class CollectorStepOutputSerializer<E, InternalCollectionT, CollectionT>(val inputElementSerializer: KSerializer<IStepOutput<E>>) : KSerializer<CollectorStepOutput<E, InternalCollectionT, CollectionT>> {
    private val inputSerializer = kotlinx.serialization.builtins.ListSerializer(inputElementSerializer)

    protected abstract fun inputToInternal(input: List<IStepOutput<E>>): InternalCollectionT
    protected abstract fun internalToOutput(internalCollection: InternalCollectionT): CollectionT

    override val descriptor: SerialDescriptor = inputSerializer.descriptor

    override fun deserialize(decoder: Decoder): CollectorStepOutput<E, InternalCollectionT, CollectionT> {
        val inputCollection = inputSerializer.deserialize(decoder)
        val internalCollection = inputToInternal(inputCollection)
        val outputCollection = internalToOutput(internalCollection)
        return CollectorStepOutput(inputCollection, internalCollection, outputCollection)
    }

    override fun serialize(encoder: Encoder, value: CollectorStepOutput<E, InternalCollectionT, CollectionT>) {
        inputSerializer.serialize(encoder, value.input)
    }
}
class ListCollectorStepOutputSerializer<E>(inputElementSerializer: KSerializer<IStepOutput<E>>) :
    CollectorStepOutputSerializer<E, List<IStepOutput<E>>, List<E>>(inputElementSerializer) {
    override fun inputToInternal(input: List<IStepOutput<E>>): List<IStepOutput<E>> {
        return input
    }
    override fun internalToOutput(internalCollection: List<IStepOutput<E>>): List<E> {
        return internalCollection.map { it.value }
    }
}
class SetCollectorStepOutputSerializer<E>(inputElementSerializer: KSerializer<IStepOutput<E>>) :
    CollectorStepOutputSerializer<E, List<IStepOutput<E>>, Set<E>>(inputElementSerializer) {
    override fun inputToInternal(input: List<IStepOutput<E>>): List<IStepOutput<E>> {
        return input
    }

    override fun internalToOutput(internalCollection: List<IStepOutput<E>>): Set<E> {
        return internalCollection.map { it.value }.toSet()
    }
}
class MapCollectorStepOutputSerializer<K, V>(inputElementSerializer: KSerializer<IStepOutput<IZip2Output<Any?, K, V>>>) :
    CollectorStepOutputSerializer<IZip2Output<Any?, K, V>, Map<K, IStepOutput<V>>, Map<K, V>>(inputElementSerializer) {
    override fun inputToInternal(input: List<IStepOutput<IZip2Output<Any?, K, V>>>): Map<K, IStepOutput<V>> {
        return input.associate {
            val zipStepOutput = it as ZipStepOutput<IZip2Output<Any?, K, V>, Any?>
            (zipStepOutput.values[0] as IStepOutput<K>).value to zipStepOutput.values[1] as IStepOutput<V>
        }
    }

    override fun internalToOutput(internalCollection: Map<K, IStepOutput<V>>): Map<K, V> {
        return internalCollection.mapValues { it.value.value }
    }
}

class ListCollectorStep<E> : CollectorStep<E, List<E>>() {
    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    override suspend fun aggregate(input: StepFlow<E>): IStepOutput<List<E>> {
        val inputList = input.toList()
        val outputList = inputList.map { it.value }
        return CollectorStepOutput(inputList, inputList, outputList)
    }
    override fun aggregate(input: Sequence<IStepOutput<E>>): IStepOutput<List<E>> {
        val inputList = input.toList()
        val outputList = inputList.map { it.value }
        return CollectorStepOutput(inputList, inputList, outputList)
    }

    @Serializable
    @SerialName("toList")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ListCollectorStep<Any?>()
        }
    }

    override fun getOutputSerializer(elementSerializer: KSerializer<IStepOutput<E>>): KSerializer<out IStepOutput<List<E>>> {
        return ListCollectorStepOutputSerializer<E>(elementSerializer)
    }

    override fun toString(): String {
        return "${getProducers().single()}.toList()"
    }
}

class SetCollectorStep<E> : CollectorStep<E, Set<E>>() {

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    override suspend fun aggregate(input: StepFlow<E>): IStepOutput<Set<E>> {
        val inputList = ArrayList<IStepOutput<E>>()
        val outputSet = HashSet<E>()
        input.collect { if (outputSet.add(it.value)) inputList.add(it) }
        return CollectorStepOutput(inputList, inputList, outputSet)
    }
    override fun aggregate(input: Sequence<IStepOutput<E>>): IStepOutput<Set<E>> {
        val inputList = ArrayList<IStepOutput<E>>()
        val outputSet = HashSet<E>()
        input.forEach { if (outputSet.add(it.value)) inputList.add(it) }
        return CollectorStepOutput(inputList, inputList, outputSet)
    }

    @Serializable
    @SerialName("toSet")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return SetCollectorStep<Any?>()
        }
    }

    override fun getOutputSerializer(elementSerializer: KSerializer<IStepOutput<E>>): KSerializer<out IStepOutput<Set<E>>> {
        return SetCollectorStepOutputSerializer<E>(elementSerializer)
    }

    override fun toString(): String {
        return "${getProducers().single()}.toSet()"
    }
}

class MapCollectorStep<K, V> : CollectorStep<IZip2Output<Any?, K, V>, Map<K, V>>() {

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    override suspend fun aggregate(input: StepFlow<IZip2Output<Any?, K, V>>): IStepOutput<Map<K, V>> {
        val inputList = ArrayList<IStepOutput<IZip2Output<Any?, K, V>>>()
        val internalMap = HashMap<K, IStepOutput<V>>()
        input.collect {
            val zipStepOutput = it as ZipStepOutput<IZip2Output<Any?, K, V>, Any?>
            if (!internalMap.containsKey(it.value.first)) {
                inputList.add(it)
                internalMap.put(zipStepOutput.values[0].value as K, zipStepOutput.values[1] as IStepOutput<V>)
            }
        }
        val outputMap: Map<K, V> = internalMap.mapValues { it.value.value }
        return CollectorStepOutput(inputList, internalMap, outputMap)
    }
    override fun aggregate(input: Sequence<IStepOutput<IZip2Output<Any?, K, V>>>): IStepOutput<Map<K, V>> {
        val inputList = ArrayList<IStepOutput<IZip2Output<Any?, K, V>>>()
        val internalMap = HashMap<K, IStepOutput<V>>()
        input.forEach {
            val zipStepOutput = it as ZipStepOutput<IZip2Output<Any?, K, V>, Any?>
            if (!internalMap.containsKey(it.value.first)) {
                inputList.add(it)
                internalMap.put(zipStepOutput.values[0].value as K, zipStepOutput.values[1] as IStepOutput<V>)
            }
        }
        val outputMap: Map<K, V> = internalMap.mapValues { it.value.value }
        return CollectorStepOutput(inputList, internalMap, outputMap)
    }

    @Serializable
    @SerialName("toMap")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MapCollectorStep<Any?, Any?>()
        }
    }

    override fun getOutputSerializer(elementSerializer: KSerializer<IStepOutput<IZip2Output<Any?, K, V>>>): KSerializer<out IStepOutput<Map<K, V>>> {
        return MapCollectorStepOutputSerializer<K, V>(elementSerializer)
    }

    override fun toString(): String {
        return "${getProducers().single()}.toMap()"
    }
}

fun <T> IFluxStep<T>.toList(): IMonoStep<List<T>> = ListCollectorStep<T>().also { connect(it) }
fun <T> IFluxStep<T>.toSet(): IMonoStep<Set<T>> = SetCollectorStep<T>().also { connect(it) }

fun <K, V> IFluxStep<IZip2Output<*, K, V>>.toMap(): IMonoStep<Map<K, V>> = MapCollectorStep<K, V>().also { connect(it) }
fun <K, V> IFluxStep<V>.associateBy(keySelector: (IMonoStep<V>) -> IMonoStep<K>): IMonoStep<Map<K, V>> {
    return map<V, IZip2Output<*, K, V>> { it.map { keySelector(it) }.allowEmpty().zip(it) }.toMap()
}
fun <K, V> IFluxStep<K>.associateWith(valueSelector: (IMonoStep<K>) -> IMonoStep<V>): IMonoStep<Map<K, V>> {
    return map { it.zip(it.map { valueSelector(it) }.allowEmpty()) }.toMap()
}
fun <In, K, V> IFluxStep<In>.associate(keySelector: (IMonoStep<In>) -> IMonoStep<K>, valueSelector: (IMonoStep<In>) -> IMonoStep<V>): IMonoStep<Map<K, V>> {
    return map { it.map { keySelector(it) }.allowEmpty().zip(it.map { valueSelector(it) }.allowEmpty()) }.toMap()
}

/**
 * Sometimes you need an additional wrapper list, but to avoid this being done accidentally it has a different name.
 */
fun <T> IMonoStep<T>.toSingletonList(): IMonoStep<List<T>> = ListCollectorStep<T>().also { connect(it) }
fun <T> IMonoStep<T>.toSingletonSet(): IMonoStep<Set<T>> = SetCollectorStep<T>().also { connect(it) }
