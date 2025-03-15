package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

class WithIndexStep<E> : MonoTransformingStep<E, IZip2Output<Any?, E, Int>>() {
    override fun requiresSingularQueryInput(): Boolean {
        return true
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<IZip2Output<Any?, E, Int>>> {
        return ZipOutputSerializer(
            arrayOf(
                getProducer().getOutputSerializer(serializationContext).upcast(),
                Int.serializer().stepOutputSerializer(null).upcast(),
            ),
        )
    }

    override fun createStream(input: StepStream<E>, context: IStreamInstantiationContext): StepStream<IZip2Output<Any?, E, Int>> {
        return input.withIndex().map { ZipStepOutput(listOf(it.value, it.index.asStepOutput(this))) }
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("withIndex")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return WithIndexStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.withIndex()"
    }
}

typealias IWithIndex<V> = IZip2Output<Any?, V, Int>

fun <T> IFluxStep<T>.withIndex(): IFluxStep<IWithIndex<T>> = WithIndexStep<T>().connectAndDowncast(this)
val IMonoStep<IWithIndex<*>>.index: IMonoStep<Int> get() = second
val <T> IMonoStep<IWithIndex<T>>.value: IMonoStep<T> get() = first
