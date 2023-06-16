package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

abstract class EqualsOperatorStep<E>(val operand: E) : MonoTransformingStep<E, Boolean>() {

    override fun createFlow(input: Flow<E>, context: IFlowInstantiationContext): Flow<Boolean> {
        return input.map { it == operand }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Boolean> {
        return serializersModule.serializer<Boolean>()
    }

    override fun toString(): String {
        return "${getProducers().single()}.equalTo($operand)"
    }
}

class IntEqualsOperatorStep(operand: Int): EqualsOperatorStep<Int>(operand) {
    override fun createDescriptor() = Descriptor(operand)

    @Serializable
    @SerialName("intEqualTo")
    class Descriptor(val operand: Int) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return IntEqualsOperatorStep(operand)
        }
    }
}

class StringEqualsOperatorStep(operand: String): EqualsOperatorStep<String>(operand) {
    override fun createDescriptor() = Descriptor(operand)

    @Serializable
    @SerialName("stringEqualTo")
    class Descriptor(val operand: String) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return StringEqualsOperatorStep(operand)
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.equalTo(\"$operand\")"
    }
}

fun IMonoStep<Int>.equalTo(operand: Int): IMonoStep<Boolean> = IntEqualsOperatorStep(operand).also { connect(it) }
fun IMonoStep<String>.equalTo(operand: String): IMonoStep<Boolean> = StringEqualsOperatorStep(operand).also { connect(it) }
