package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class IdentityStep<E> : TransformingStep<E, E>(), IFluxOrMonoStep<E> {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out E> {
        return getProducer().getOutputSerializer(serializersModule)
    }

    override fun createFlow(input: Flow<E>, context: IFlowInstantiationContext): Flow<E> {
        return input
    }

    override fun createDescriptor(): StepDescriptor {
        return IdentityStepDescriptor()
    }

    @Serializable
    @SerialName("identity")
    class IdentityStepDescriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return IdentityStep<Any?>()
        }
    }
}
