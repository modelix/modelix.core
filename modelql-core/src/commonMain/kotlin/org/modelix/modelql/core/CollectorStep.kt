package org.modelix.modelql.core

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

abstract class CollectorStep<E, CollectionT : Collection<E>>() : AggregationStep<E, CollectionT>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<CollectionT>> {
        val element = getProducers().first().getOutputSerializer(serializersModule)
        return getOutputSerializer(element.upcast())
    }

    protected abstract fun getOutputSerializer(elementSerializer: KSerializer<IStepOutput<E>>): KSerializer<out IStepOutput<CollectionT>>
}

abstract class CollectorStepOutput<E, CollectionT : Collection<E>, InternalCollectionT : Collection<IStepOutput<E>>>(
    val collection: InternalCollectionT
) : IStepOutput<CollectionT>
class ListCollectorStepOutput<E>(collection: List<IStepOutput<E>>) : CollectorStepOutput<E, List<E>, List<IStepOutput<E>>>(collection) {
    override val value: List<E>
        get() = collection.map { it.value }
}
class SetCollectorStepOutput<E>(collection: Set<IStepOutput<E>>) : CollectorStepOutput<E, Set<E>, Set<IStepOutput<E>>>(collection) {
    override val value: Set<E>
        get() = collection.map { it.value }.toSet()
}

abstract class CollectorStepOutputSerializer<E, CollectionT : Collection<E>, InternalCollectionT : Collection<IStepOutput<E>>>(private val collectionSerializer: KSerializer<InternalCollectionT>) : KSerializer<CollectorStepOutput<E, CollectionT, InternalCollectionT>> {

    protected abstract fun wrap(collection: InternalCollectionT): CollectorStepOutput<E, CollectionT, InternalCollectionT>

    override val descriptor: SerialDescriptor = collectionSerializer.descriptor

    override fun deserialize(decoder: Decoder): CollectorStepOutput<E, CollectionT, InternalCollectionT> {
        return wrap(decoder.decodeSerializableValue(collectionSerializer))
    }

    override fun serialize(encoder: Encoder, value: CollectorStepOutput<E, CollectionT, InternalCollectionT>) {
        encoder.encodeSerializableValue(collectionSerializer, value.collection)
    }
}
class ListCollectorStepOutputSerializer<E>(collectionSerializer: KSerializer<List<IStepOutput<E>>>) :
    CollectorStepOutputSerializer<E, List<E>, List<IStepOutput<E>>>(collectionSerializer) {
    override fun wrap(collection: List<IStepOutput<E>>): ListCollectorStepOutput<E> {
        return ListCollectorStepOutput(collection)
    }
}
class SetCollectorStepOutputSerializer<E>(collectionSerializer: KSerializer<Set<IStepOutput<E>>>) :
    CollectorStepOutputSerializer<E, Set<E>, Set<IStepOutput<E>>>(collectionSerializer) {
    override fun wrap(collection: Set<IStepOutput<E>>): SetCollectorStepOutput<E> {
        return SetCollectorStepOutput(collection)
    }
}

class ListCollectorStep<E> : CollectorStep<E, List<E>>() {
    override fun createDescriptor() = Descriptor()

    override suspend fun aggregate(input: StepFlow<E>): IStepOutput<List<E>> = ListCollectorStepOutput(input.toList())
    override fun aggregate(input: Sequence<IStepOutput<E>>): IStepOutput<List<E>> = ListCollectorStepOutput(input.toList())

    @Serializable
    @SerialName("toList")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return ListCollectorStep<Any?>()
        }
    }

    override fun getOutputSerializer(elementSerializer: KSerializer<IStepOutput<E>>): KSerializer<out IStepOutput<List<E>>> {
        return ListCollectorStepOutputSerializer<E>(ListSerializer(elementSerializer))
    }

    override fun toString(): String {
        return "${getProducers().single()}.toList()"
    }
}

class SetCollectorStep<E> : CollectorStep<E, Set<E>>() {

    override fun createDescriptor() = Descriptor()

    override suspend fun aggregate(input: StepFlow<E>): IStepOutput<Set<E>> = SetCollectorStepOutput(input.toSet())
    override fun aggregate(input: Sequence<IStepOutput<E>>): IStepOutput<Set<E>> = SetCollectorStepOutput(input.toSet())

    @Serializable
    @SerialName("toSet")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return SetCollectorStep<Any?>()
        }
    }

    override fun getOutputSerializer(elementSerializer: KSerializer<IStepOutput<E>>): KSerializer<out IStepOutput<Set<E>>> {
        return SetCollectorStepOutputSerializer<E>(SetSerializer(elementSerializer))
    }

    override fun toString(): String {
        return "${getProducers().single()}.toSet()"
    }
}

fun <T> IFluxStep<T>.toList(): IMonoStep<List<T>> = ListCollectorStep<T>().also { connect(it) }
fun <T> IFluxStep<T>.toSet(): IMonoStep<Set<T>> = SetCollectorStep<T>().also { connect(it) }

/**
 * Sometimes you need an additional wrapper list, but to avoid this being done accidentally it has a different name.
 */
fun <T> IMonoStep<T>.toSingletonList(): IMonoStep<List<T>> = ListCollectorStep<T>().also { connect(it) }
fun <T> IMonoStep<T>.toSingletonSet(): IMonoStep<Set<T>> = SetCollectorStep<T>().also { connect(it) }
