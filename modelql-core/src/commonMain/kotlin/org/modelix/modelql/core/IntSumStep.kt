package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class IntSumStep(val operand: Int) : MonoTransformingStep<Int, Int>() {

    override fun transform(input: Int): Int {
        return input + operand
    }

    override fun createDescriptor() = IntSumDescriptor(operand)

    @Serializable
    @SerialName("intSum")
    class IntSumDescriptor(val operand: Int) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IntSumStep(operand)
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Int>> {
        return serializersModule.serializer<Int>().stepOutputSerializer()
    }
}

operator fun IMonoStep<Int>.plus(other: Int) = IntSumStep(other).connectAndDowncast(this)
operator fun IFluxStep<Int>.plus(other: Int) = IntSumStep(other).connectAndDowncast(this)
operator fun Int.plus(other: IMonoStep<Int>): IMonoStep<Int> = other.plus(this)
operator fun Int.plus(other: IFluxStep<Int>): IFluxStep<Int> = other.plus(this)
