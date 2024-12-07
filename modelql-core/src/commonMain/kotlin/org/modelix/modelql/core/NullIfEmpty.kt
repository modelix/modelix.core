package org.modelix.modelql.core

import com.badoo.reaktive.observable.defaultIfEmpty
import com.badoo.reaktive.observable.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class NullIfEmpty<E>() : MonoTransformingStep<E, E?>() {

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E?>> {
        return MultiplexedOutputSerializer(
            this,
            listOf(
                getProducer().getOutputSerializer(serializationContext).upcast(),
                nullSerializer<E?>().stepOutputSerializer(this).upcast(),
            ),
        )
    }

    override fun createStream(input: StepStream<E>, context: IStreamInstantiationContext): StepStream<E?> {
        val downcast: StepStream<E?> = input
        return downcast.map { MultiplexedOutput(0, it) }
            .defaultIfEmpty(MultiplexedOutput(1, null.asStepOutput(this@NullIfEmpty)))
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = OrNullDescriptor()

    @Serializable
    @SerialName("orNull")
    class OrNullDescriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return NullIfEmpty<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = OrNullDescriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.orNull()"
    }

    override fun canBeEmpty(): Boolean {
        return false
    }
}

fun <Out> IMonoStep<Out>.orNull(): IMonoStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)
fun <Out> IFluxStep<Out>.orNull(): IFluxStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)

fun <Out> IMonoStep<Out>.nullIfEmpty(): IMonoStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)
fun <Out> IFluxStep<Out>.nullIfEmpty(): IFluxStep<Out?> = NullIfEmpty<Out>().connectAndDowncast(this)
