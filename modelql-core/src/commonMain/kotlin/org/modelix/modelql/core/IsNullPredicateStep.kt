package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class IsNullPredicateStep<In>() : MonoTransformingStep<In, Boolean>() {
    override fun createFlow(input: Flow<In>, context: IFlowInstantiationContext): Flow<Boolean> {
        return input.map { it == null }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Boolean> {
        return serializersModule.serializer<Boolean>()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("isNull")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return IsNullPredicateStep<Any?>()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.isNull()"""
    }
}

fun <T> IMonoStep<T>.isNull(): IMonoStep<Boolean> = IsNullPredicateStep<T>().also { connect(it) }
fun <T : Any> IMonoStep<T?>.filterNotNull(): IMonoStep<T> = filter { !it.isNull() } as IMonoStep<T>
fun <T : Any> IFluxStep<T?>.filterNotNull(): IFluxStep<T> = filter { !it.isNull() } as IFluxStep<T>