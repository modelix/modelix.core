package org.modelix.modelql.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class AndOperatorStep() : MonoTransformingStep<IZipOutput<Boolean>, Boolean>() {
    override fun transform(element: IZipOutput<Boolean>): Sequence<Boolean> {
        return sequenceOf(element.values.all { it })
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
        return getProducers().joinToString(" and ")
    }
}

infix fun IMonoStep<Boolean>.and(other: IMonoStep<Boolean>): IMonoStep<Boolean> {
    val zip = zip(other)
    return AndOperatorStep().also {
        zip.connect(it)
    }
}
