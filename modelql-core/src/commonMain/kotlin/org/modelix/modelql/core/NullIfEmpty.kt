package org.modelix.modelql.core

import kotlinx.coroutines.flow.onEmpty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.modules.SerializersModule

class NullIfEmpty<E>() : MonoTransformingStep<E, E?>() {

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E?>> {
        return getProducer().getOutputSerializer(serializersModule).upcast()
    }

    override fun createFlow(input: StepFlow<E>, context: IFlowInstantiationContext): StepFlow<E?> {
        val downcast: StepFlow<E?> = input
        return downcast.onEmpty { emit(SimpleStepOutput(null)) }
    }

    override fun transform(input: E): E? {
        return input
    }

    override fun createDescriptor() = OrNullDescriptor()

    @Serializable
    @SerialName("orNull")
    class OrNullDescriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return NullIfEmpty<Any?>()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.orNull()"""
    }

    override fun canBeEmpty(): Boolean {
        return false
    }
}

fun <Out> IMonoStep<Out>.orNull(): IMonoStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)
fun <Out> IFluxStep<Out>.orNull(): IFluxStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)

fun <Out> IMonoStep<Out>.nullIfEmpty(): IMonoStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)
fun <Out> IFluxStep<Out>.nullIfEmpty(): IFluxStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)
