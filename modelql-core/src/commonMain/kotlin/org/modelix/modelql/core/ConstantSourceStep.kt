package org.modelix.modelql.core

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
    override fun requiresSingularQueryInput(): Boolean = false
    override fun hasSideEffect(): Boolean = false
    override fun requiresWriteAccess(): Boolean = false
    override fun needsCoroutineScope(): Boolean = false

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<E> {
        return sequenceOf(element)
    }

    override fun evaluate(queryInput: Any?): Optional<E> {
        return Optional.of(element)
    }

    override fun createFlow(context: IFlowInstantiationContext): StepFlow<E> {
        return flowOf(SimpleStepOutput(element))
    }

    override fun toString(): String {
        return """Mono($element)"""
    }
}

class StringSourceStep(element: String?) : ConstantSourceStep<String?>(element) {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<String?>> {
        return serializersModule.serializer<String>().nullable.stepOutputSerializer()
    }

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor(element)

    @Serializable
    @SerialName("stringMonoSource")
    class Descriptor(val element: String?) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return StringSourceStep(element)
        }
    }

    override fun toString(): String {
        return """Mono(${element?.let { "\"$it\"" }})"""
    }
}

class BooleanSourceStep(element: Boolean) : ConstantSourceStep<Boolean>(element) {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer()
    }

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor(element)

    @Serializable
    @SerialName("booleanMonoSource")
    class Descriptor(val element: Boolean) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return BooleanSourceStep(element)
        }
    }
}

fun <T : String?> T.asMono(): IMonoStep<T> = StringSourceStep(this) as IMonoStep<T>
fun Boolean.asMono(): IMonoStep<Boolean> = BooleanSourceStep(this)
