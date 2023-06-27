package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

abstract class ConstantSourceStep<E>(val element: E) : ProducingStep<E>(), IMonoStep<E> {
    override fun canBeEmpty(): Boolean = false

    override fun canBeMultiple(): Boolean = false

    override fun evaluate(input: Any?): E {
        return element
    }

    override fun createFlow(context: IFlowInstantiationContext): Flow<E> {
        return flowOf(element)
    }

    override fun toString(): String {
        return """Mono($element)"""
    }
}

class StringSourceStep(element: String?) : ConstantSourceStep<String?>(element) {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<String?> {
        return serializersModule.serializer<String>().nullable
    }

    override fun createDescriptor() = Descriptor(element)

    @Serializable
    @SerialName("stringMonoSource")
    class Descriptor(val element: String?) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return StringSourceStep(element)
        }
    }

    override fun toString(): String {
        return """Mono(${element?.let { "\"$it\"" }})"""
    }
}

class BooleanSourceStep(element: Boolean) : ConstantSourceStep<Boolean>(element) {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Boolean> {
        return serializersModule.serializer<Boolean>()
    }

    override fun createDescriptor() = Descriptor(element)

    @Serializable
    @SerialName("booleanMonoSource")
    class Descriptor(val element: Boolean) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return BooleanSourceStep(element)
        }
    }
}

fun <T : String?> T.asMono(): IMonoStep<T> = StringSourceStep(this) as IMonoStep<T>
fun Boolean.asMono(): IMonoStep<Boolean> = BooleanSourceStep(this)
