package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class StringToBooleanStep : MonoTransformingStep<String?, Boolean>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Boolean> {
        return serializersModule.serializer<Boolean>()
    }

    override fun createFlow(input: Flow<String?>, context: IFlowInstantiationContext): Flow<Boolean> {
        return input.map { it?.toBoolean() ?: false }
    }

    override fun createDescriptor(): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("toBoolean")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return StringToBooleanStep()
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.toBoolean()"
    }
}

fun IMonoStep<String?>.toBoolean() = StringToBooleanStep().connectAndDowncast(this)
fun IFluxStep<String?>.toBoolean() = StringToBooleanStep().connectAndDowncast(this)
