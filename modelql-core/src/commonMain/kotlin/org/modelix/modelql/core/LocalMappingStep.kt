package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule

class LocalMappingStep<In, Out>(val transformation: (In) -> Out) : MonoTransformingStep<In, Out>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out Out> {
        return LocalMappingSerializer(this, getProducer().getOutputSerializer(serializersModule))
    }

    override fun createFlow(input: Flow<In>, context: IFlowInstantiationContext): Flow<Out> {
        return input.map(transformation)
    }

    override fun createDescriptor(): StepDescriptor {
        return IdentityStep.IdentityStepDescriptor()
    }
}

class LocalMappingSerializer<In, Out>(val step: LocalMappingStep<In, Out>, val inputSerializer: KSerializer<out In>) : KSerializer<Out> {
    override fun deserialize(decoder: Decoder): Out {
        return step.transformation(decoder.decodeSerializableValue(inputSerializer))
    }

    override val descriptor: SerialDescriptor
        get() = inputSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Out) {
        throw UnsupportedOperationException("Local mappings are applied after receiving the query result. Their output is not expected to be serialized.")
    }
}

fun <In, Out> IMonoStep<In>.mapLocal(body: (In) -> Out): IMonoStep<Out> {
    return LocalMappingStep(body).also { connect(it) }
}

fun <In, Out> IFluxStep<In>.mapLocal(body: (In) -> Out): IFluxStep<Out> {
    return map { it.mapLocal(body) }
}
