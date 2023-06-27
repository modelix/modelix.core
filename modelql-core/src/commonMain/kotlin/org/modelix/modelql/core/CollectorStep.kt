package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.modules.SerializersModule

abstract class CollectorStep<E, CollectionT : Collection<E>>() : AggregationStep<E, CollectionT>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out CollectionT> {
        val element = getProducers().first().getOutputSerializer(serializersModule)
        return getOutputSerializer(element)
    }

    protected abstract fun getOutputSerializer(elementSerializer: KSerializer<out E>): KSerializer<out CollectionT>
}

class ListCollectorStep<E> : CollectorStep<E, List<E>>() {
    override fun createDescriptor() = Descriptor()

    override suspend fun aggregate(input: Flow<E>): List<E> = input.toList()
    override fun aggregate(input: Sequence<E>): List<E> = input.toList()

    @Serializable
    @SerialName("toList")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return ListCollectorStep<Any?>()
        }
    }

    override fun getOutputSerializer(elementSerializer: KSerializer<out E>): KSerializer<out List<E>> {
        return ListSerializer(elementSerializer)
    }

    override fun toString(): String {
        return "${getProducers().single()}.toList()"
    }
}

class SetCollectorStep<E> : CollectorStep<E, Set<E>>() {

    override fun createDescriptor() = Descriptor()

    override suspend fun aggregate(input: Flow<E>): Set<E> = input.toSet()
    override fun aggregate(input: Sequence<E>): Set<E> = input.toSet()

    @Serializable
    @SerialName("toSet")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return SetCollectorStep<Any?>()
        }
    }

    override fun getOutputSerializer(elementSerializer: KSerializer<out E>): KSerializer<out Set<E>> {
        return SetSerializer(elementSerializer)
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
