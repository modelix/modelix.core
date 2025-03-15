package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class PrintStep<E>(val prefix: String) : MonoTransformingStep<E, E>() {
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializationContext)
    }

    override fun createStream(input: StepStream<E>, context: IStreamInstantiationContext): StepStream<E> {
        return input.map {
            println(prefix + it.value)
            it
        }
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor = Descriptor(prefix)

    @Serializable
    @SerialName("print")
    data class Descriptor(val prefix: String = "") : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return PrintStep<Any?>(prefix)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(prefix)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.print(\"$prefix\")"
    }
}

fun <T> IMonoStep<T>.print(prefix: String = "") = PrintStep<T>(prefix).connectAndDowncast(this)
fun <T> IFluxStep<T>.print(prefix: String = "") = PrintStep<T>(prefix).connectAndDowncast(this)
