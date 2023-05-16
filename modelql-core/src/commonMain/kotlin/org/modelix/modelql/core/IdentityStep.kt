package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class IdentityStep<RemoteE> : TransformingStep<RemoteE, RemoteE>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<*> {
        return getProducers().first().getOutputSerializer(serializersModule)
    }

    override fun transform(element: RemoteE): Sequence<RemoteE> {
        return sequenceOf(element)
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