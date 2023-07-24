package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class InPredicate<E>() : TransformingStepWithParameter<E, Set<E>, Any?, Boolean>() {

    override fun transformElement(input: E, parameter: Set<E>?): Boolean {
        return parameter?.contains(input) ?: false
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer()
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("inSet")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return InPredicate<Any?>()
        }
    }

    override fun toString(): String {
        return """${getInputProducer()}.inSet(${getParameterProducer()})"""
    }
}

fun <T> IMonoStep<T>.inSet(values: IMonoStep<Set<T>>): IMonoStep<Boolean> = InPredicate<T>().also {
    connect(it)
    values.connect(it)
}

fun <T> IFluxStep<T>.inSet(values: IMonoStep<Set<T>>): IFluxStep<Boolean> = InPredicate<T>().also {
    connect(it)
    values.connect(it)
}

fun IMonoStep<String?>.inSet(values: Set<String>) = inSet(values.asMono())
fun IFluxStep<String?>.inSet(values: Set<String>) = inSet(values.asMono())
