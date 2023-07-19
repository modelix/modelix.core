package org.modelix.modelql.core

import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

class CollectorStepOutput<E, CollectionT : Collection<E>>(
    val input: List<IStepOutput<E>>,
    val output: CollectionT
) : IStepOutput<CollectionT> {
    override val value: CollectionT get() = output
}

abstract class CollectorStepOutputSerializer<E, CollectionT : Collection<E>>(private val inputElementSerializer: KSerializer<IStepOutput<E>>) : KSerializer<CollectorStepOutput<E, CollectionT>> {
    private val inputSerializer = kotlinx.serialization.builtins.ListSerializer(inputElementSerializer)

    protected abstract fun wrap(input: List<IStepOutput<E>>): CollectorStepOutput<E, CollectionT>

    override val descriptor: SerialDescriptor = inputSerializer.descriptor

    override fun deserialize(decoder: Decoder): CollectorStepOutput<E, CollectionT> {
        return wrap(decoder.decodeSerializableValue(inputSerializer))
    }

    override fun serialize(encoder: Encoder, value: CollectorStepOutput<E, CollectionT>) {
        encoder.encodeSerializableValue(inputSerializer, value.input)
    }
}
class ListCollectorStepOutputSerializer<E>(inputElementSerializer: KSerializer<IStepOutput<E>>) :
    CollectorStepOutputSerializer<E, List<E>>(inputElementSerializer) {
    override fun wrap(input: List<IStepOutput<E>>): CollectorStepOutput<E, List<E>> {
        return CollectorStepOutput(input, input.map { it.value })
    }
}
class SetCollectorStepOutputSerializer<E>(inputElementSerializer: KSerializer<IStepOutput<E>>) :
    CollectorStepOutputSerializer<E, Set<E>>(inputElementSerializer) {
    override fun wrap(input: List<IStepOutput<E>>): CollectorStepOutput<E, Set<E>> {
        return CollectorStepOutput(input, input.map { it.value }.toSet())
    }
}

class ListCollectorStep<E> : CollectorStep<E, List<E>>() {
    override fun createDescriptor(context: QuerySerializationContext) = Descriptor()

    override suspend fun aggregate(input: StepFlow<E>): IStepOutput<List<E>> {
        val inputList = input.toList()
        val outputList = inputList.map { it.value }
        return CollectorStepOutput(inputList, outputList)
    }
    override fun aggregate(input: Sequence<IStepOutput<E>>): IStepOutput<List<E>> {
        val inputList = input.toList()
        val outputList = inputList.map { it.value }
        return CollectorStepOutput(inputList, outputList)
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

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor()

    override suspend fun aggregate(input: StepFlow<E>): IStepOutput<Set<E>> {
        val inputList = ArrayList<IStepOutput<E>>()
        val outputSet = HashSet<E>()
        input.collect { if (outputSet.add(it.value)) inputList.add(it) }
        return CollectorStepOutput(inputList, outputSet)
    }
    override fun aggregate(input: Sequence<IStepOutput<E>>): IStepOutput<Set<E>> {
        val inputList = ArrayList<IStepOutput<E>>()
        val outputSet = HashSet<E>()
        input.forEach { if (outputSet.add(it.value)) inputList.add(it) }
        return CollectorStepOutput(inputList, outputSet)
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

fun <T> IFluxStep<T>.toList(): IMonoStep<List<T>> = ListCollectorStep<T>().also { connect(it) }
fun <T> IFluxStep<T>.toSet(): IMonoStep<Set<T>> = SetCollectorStep<T>().also { connect(it) }

fun <T> IFluxStep<T>.distinct(): IFluxStep<T> = TODO()

/**
 * Sometimes you need an additional wrapper list, but to avoid this being done accidentally it has a different name.
 */
fun <T> IMonoStep<T>.toSingletonList(): IMonoStep<List<T>> = ListCollectorStep<T>().also { connect(it) }
fun <T> IMonoStep<T>.toSingletonSet(): IMonoStep<Set<T>> = SetCollectorStep<T>().also { connect(it) }
