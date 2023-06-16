package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class IntSumStep(val operand: Int): TransformingStep<Int, Int>() {
    override fun createFlow(input: Flow<Int>, context: IFlowInstantiationContext): Flow<Int> {
        return input.map { it + operand }
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

