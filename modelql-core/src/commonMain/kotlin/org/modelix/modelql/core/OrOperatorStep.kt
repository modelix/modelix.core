package org.modelix.modelql.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class OrOperatorStep() : MonoTransformingStep<IZipOutput<Boolean>, Boolean>() {
    override fun transform(element: IZipOutput<Boolean>): Sequence<Boolean> {
        return sequenceOf(element.values.any { it })
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("or")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return OrOperatorStep()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule) = serializersModule.serializer<Boolean>()

    override fun toString(): String {
        return getProducers().joinToString(" or ")
    }
}

infix fun IMonoStep<Boolean>.or(other: IMonoStep<Boolean>): IMonoStep<Boolean> {
    val zip = zip(other)
    return OrOperatorStep().also {
        zip.connect(it)
    }
}
