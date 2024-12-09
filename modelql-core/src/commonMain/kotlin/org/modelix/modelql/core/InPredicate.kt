package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class InPredicate<E>() : TransformingStepWithParameter<E, Set<E>, Any?, Boolean>() {

    override fun transformElement(input: IStepOutput<E>, parameter: IStepOutput<Set<E>>?): IStepOutput<Boolean> {
        return (parameter?.value?.contains(input.value) == true).asStepOutput(this)
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Boolean>> {
        return serializationContext.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("inSet")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return InPredicate<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun toString(): String {
        return "${getInputProducer()}\n.inSet(${getParameterProducer()})"
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
