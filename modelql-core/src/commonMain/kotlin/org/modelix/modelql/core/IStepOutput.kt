package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.modelix.streams.IStream

/**
 * Can carry some additional data required for processing the result on the client side.
 */
interface IStepOutput<out E> {
    val value: E
}

fun <T> IStepOutput<*>.upcast(): IStepOutput<T> = this as IStepOutput<T>

typealias StepStream<E> = IStream.Many<IStepOutput<E>>
typealias MonoStepStream<E> = IStream.One<IStepOutput<E>>
val <T> IStream.Many<IStepOutput<T>>.value: IStream.Many<T> get() = map { it.value }
fun <T> IStream.Many<T>.asStepStream(owner: IProducingStep<T>?): StepStream<T> = map { it.asStepOutput(owner) }
fun <T> IStream.One<T>.asStepStream(owner: IProducingStep<T>?): MonoStepStream<T> = map { it.asStepOutput(owner) }
fun <T> StepStream<*>.upcast(): StepStream<T> = this as StepStream<T>

class SimpleStepOutput<out E>(override val value: E, val owner: IProducingStep<E>?) : IStepOutput<E> {
    override fun toString(): String {
        return "SimpleStepOutput[$value]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SimpleStepOutput<*>

        return value == other.value
    }

    override fun hashCode(): Int {
        return value?.hashCode() ?: 0
    }
}

class SimpleStepOutputSerializer<E>(val valueSerializer: KSerializer<E>, val owner: IProducingStep<E>?) : KSerializer<SimpleStepOutput<E>> {
    init {
        require(valueSerializer !is SimpleStepOutputSerializer<*>)
        require(valueSerializer !is ZipOutputSerializer<*, *>)
    }
    override fun deserialize(decoder: Decoder): SimpleStepOutput<E> {
        return SimpleStepOutput(valueSerializer.deserialize(decoder), owner)
    }

    override val descriptor: SerialDescriptor
        get() = valueSerializer.descriptor

    override fun serialize(encoder: Encoder, value: SimpleStepOutput<E>) {
        valueSerializer.serialize(encoder, value.value)
    }
}

fun <T> KSerializer<T>.stepOutputSerializer(owner: IProducingStep<T>?): SimpleStepOutputSerializer<T> = SimpleStepOutputSerializer(this, owner)

fun <T> T.asStepOutput(owner: IProducingStep<T>?): IStepOutput<T> = SimpleStepOutput(this, owner)
