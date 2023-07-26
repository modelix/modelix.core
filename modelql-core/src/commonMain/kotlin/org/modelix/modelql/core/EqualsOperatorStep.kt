package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class EqualsOperatorStep<E>() : TransformingStepWithParameter<E, E, E, Boolean>() {

    override fun transformElement(input: E, parameter: E?): Boolean {
        return input == parameter
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "${getInputProducer()}.equalTo(${getParameterProducer()})"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("equalTo")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return EqualsOperatorStep<Any?>()
        }
    }
}

fun <T> IMonoStep<T>.notEqualTo(operand: IMonoStep<T>): IMonoStep<Boolean> = !equalTo(operand)
fun <T> IMonoStep<T>.equalTo(operand: IMonoStep<T>): IMonoStep<Boolean> = EqualsOperatorStep<T>().also {
    connect(it)
    operand.connect(it)
}

fun IMonoStep<Boolean?>.equalTo(operand: Boolean?) = equalTo(operand.asMono())
fun IMonoStep<Boolean?>.notEqualTo(operand: Boolean?) = !equalTo(operand)

fun IMonoStep<Byte?>.equalTo(operand: Byte?) = equalTo(operand.asMono())
fun IMonoStep<Byte?>.notEqualTo(operand: Byte?) = !equalTo(operand)

fun IMonoStep<Char?>.equalTo(operand: Char?) = equalTo(operand.asMono())
fun IMonoStep<Char?>.notEqualTo(operand: Char?) = !equalTo(operand)

fun IMonoStep<Short?>.equalTo(operand: Short?) = equalTo(operand.asMono())
fun IMonoStep<Short?>.notEqualTo(operand: Short?) = !equalTo(operand)

fun IMonoStep<Int?>.equalTo(operand: Int?) = equalTo(operand.asMono())
fun IMonoStep<Int?>.notEqualTo(operand: Int?) = !equalTo(operand)

fun IMonoStep<Long?>.equalTo(operand: Long?) = equalTo(operand.asMono())
fun IMonoStep<Long?>.notEqualTo(operand: Long?) = !equalTo(operand)

fun IMonoStep<Float?>.equalTo(operand: Float?) = equalTo(operand.asMono())
fun IMonoStep<Float?>.notEqualTo(operand: Float?) = !equalTo(operand)

fun IMonoStep<Double?>.equalTo(operand: Double?) = equalTo(operand.asMono())
fun IMonoStep<Double?>.notEqualTo(operand: Double?) = !equalTo(operand)

fun IMonoStep<String?>.equalTo(operand: String) = equalTo(operand.asMono())
fun IMonoStep<String?>.notEqualTo(operand: String) = !equalTo(operand)
