package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEmpty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.modules.SerializersModule

class NullIfEmpty<E>() : MonoTransformingStep<E, E?>() {

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<E?> {
        val outputSerializer = getProducer().getOutputSerializer(serializersModule) as KSerializer<Any>
        return outputSerializer.nullable as KSerializer<E?>
    }

    override fun createFlow(input: Flow<E>, context: IFlowInstantiationContext): Flow<E?> {
        val downcast: Flow<E?> = input
        return downcast.onEmpty { emit(null) }
    }

    override fun transform(input: E): E? {
        return input
    }

    override fun createDescriptor() = OrNullDescriptor()

    @Serializable
    @SerialName("orNull")
    class OrNullDescriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return NullIfEmpty<Any?>()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.orNull()"""
    }
}

fun <Out> IMonoStep<Out>.orNull(): IMonoStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)
fun <Out> IFluxStep<Out>.orNull(): IFluxStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)
