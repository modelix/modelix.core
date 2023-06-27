package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class AndOperatorStep() : MonoTransformingStep<IZipOutput<Boolean>, Boolean>() {
    override fun createFlow(input: Flow<IZipOutput<Boolean>>, context: IFlowInstantiationContext): Flow<Boolean> {
        return input.map { it.values.all { it == true } }
    }

    override fun transform(input: IZipOutput<Boolean>): Boolean {
        return input.values.all { it == true }
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("and")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return AndOperatorStep()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule) = serializersModule.serializer<Boolean>()

    override fun toString(): String {
        return "and(" + getProducers().joinToString(", ") + ")"
    }
}

infix fun IMonoStep<Boolean>.and(other: IMonoStep<Boolean>): IMonoStep<Boolean> {
    val zip = zip(other)
    return AndOperatorStep().connectAndDowncast(zip)
}
