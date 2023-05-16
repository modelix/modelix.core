package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class IntSumStep(val operand: Int): TransformingStep<Int, Int>() {
    override fun transform(element: Int): Sequence<Int> {
        return sequenceOf(element + operand)
    }

    override fun createDescriptor() = IntSumDescriptor(operand)

    @Serializable
    @SerialName("intSum")
    class IntSumDescriptor(val operand: Int) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return IntSumStep(operand)
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Int> {
        return serializersModule.serializer<Int>()
    }
}

operator fun IProducingStep<Int>.plus(other: Int): IProducingStep<Int> = IntSumStep(other).also { connect(it) }
operator fun Int.plus(other: IProducingStep<Int>): IProducingStep<Int> = other.plus(this)

