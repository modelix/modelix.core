package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class PrintStep<E>(val prefix: String) : MonoTransformingStep<E, E>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out E> {
        return getProducer().getOutputSerializer(serializersModule)
    }

    override fun createFlow(input: Flow<E>, context: IFlowInstantiationContext): Flow<E> {
        return input.map {
            println(prefix + it)
            it
        }
    }

    override fun createDescriptor(): StepDescriptor = Descriptor(prefix)

    @Serializable
    @SerialName("print")
    class Descriptor(val prefix: String = "") : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return PrintStep<Any?>(prefix)
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.print(\"$prefix\")"
    }
}

fun <T> IMonoStep<T>.print(prefix: String = "") = PrintStep<T>(prefix).connectAndDowncast(this)
fun <T> IFluxStep<T>.print(prefix: String = "") = PrintStep<T>(prefix).connectAndDowncast(this)
