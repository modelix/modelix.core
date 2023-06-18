package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEmpty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.modules.SerializersModule

class NullIfEmpty<RemoteE>() : MonoTransformingStep<RemoteE, RemoteE?>() {

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<RemoteE?> {
        val outputSerializer = getProducer().getOutputSerializer(serializersModule) as KSerializer<Any>
        return outputSerializer.nullable as KSerializer<RemoteE?>
    }

    override fun createFlow(input: Flow<RemoteE>, context: IFlowInstantiationContext): Flow<RemoteE?> {
        val downcast: Flow<RemoteE?> = input
        return downcast.onEmpty { emit(null) }
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

fun <RemoteOut> IMonoStep<RemoteOut>.orNull(): IMonoStep<RemoteOut?> = NullIfEmpty<RemoteOut>().also { connect(it) }
