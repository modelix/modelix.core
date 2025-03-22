package org.modelix.modelql.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeCollection
import org.modelix.streams.IStream

open class ZipStep<CommonIn, Out : ZipNOutputC<CommonIn>>() : ProducingStep<Out>(), IConsumingStep<CommonIn>, IMonoStep<Out>, IFluxStep<Out> {
    private val producers = ArrayList<IProducingStep<CommonIn>>()

    override fun canBeEmpty(): Boolean = producers.any { it.canBeEmpty() }

    override fun canBeMultiple(): Boolean = producers.any { it.canBeMultiple() }

    override fun requiresSingularQueryInput(): Boolean {
        return true
//        if (producers.any { !it.isSingle() }) return true
//        return super<ProducingStep>.requiresSingularQueryInput()
    }

    override fun validate() {
        super<ProducingStep>.validate()
        for (producer in producers) {
            if (producer.canBeEmpty() && producer !is AllowEmptyStep) {
                throw RuntimeException("zip input is not allowed to be empty (use allowEmpty(), nullIfEmpty(), or assertNotEmpty()): $producer")
            }
        }
    }

    override fun toString(): String {
        return "zip(\n${getProducers().joinToString("\n,\n") { it.toString().prependIndent("  ") }}\n)"
    }

    override fun addProducer(producer: IProducingStep<CommonIn>) {
        if (getProducers().contains(producer)) return
        producers.add(producer)
        producer.addConsumer(this)
    }

    override fun getProducers(): List<IProducingStep<CommonIn>> {
        return producers
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("zip")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ZipStep<Any?, ZipNOutput>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        val elementSerializers: Array<KSerializer<IStepOutput<CommonIn>>> = producers.map {
            it.getOutputSerializer(serializationContext).upcast()
        }.toTypedArray()
        return ZipOutputSerializer(elementSerializers)
    }

    private fun ZipNOutputC<CommonIn>.upcast(): Out = this as Out

    override fun createStream(context: IStreamInstantiationContext): IStream.Many<ZipStepOutput<Out, CommonIn>> {
        val inputStreams: List<IStream.Many<IStepOutput<CommonIn>>> = producers.map {
            val possiblyEmptyStreams = context.getOrCreateStream(it)
            if (it is AllowEmptyStep) {
                possiblyEmptyStreams
            } else {
                possiblyEmptyStreams.assertNotEmpty { producers.toString() }
            }
        }

        return inputStreams.zipRepeating().map { ZipStepOutput<Out, CommonIn>(it) }
    }
}

class AllowEmptyStep<E>() : IdentityStep<E>() {
    override fun toString(): String {
        return "${getProducer()}\n.allowEmpty()"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("allowEmpty")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return AllowEmptyStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}
fun <T> IProducingStep<T>.allowEmpty(): IProducingStep<T> = AllowEmptyStep<T>().also { connect(it) }
fun <T> IFluxStep<T>.allowEmpty(): IFluxStep<T> = AllowEmptyStep<T>().also { connect(it) }
fun <T> IMonoStep<T>.allowEmpty(): IMonoStep<T> = AllowEmptyStep<T>().also { connect(it) }

class AssertNotEmptyStep<E>() : IdentityStep<E>() {
    override fun canBeEmpty(): Boolean {
        return false
    }

    override fun createStream(input: StepStream<E>, context: IStreamInstantiationContext): StepStream<E> {
        return input.assertNotEmpty { "$this" }
    }

    override fun toString(): String {
        return "${getProducer()}\n.assertNotEmpty()"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("assertNotEmpty")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return AssertNotEmptyStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}

fun <T> IFluxStep<T>.assertNotEmpty(): IFluxStep<T> = AssertNotEmptyStep<T>().also { connect(it) }
fun <T> IMonoStep<T>.assertNotEmpty(): IMonoStep<T> = AssertNotEmptyStep<T>().also { connect(it) }

typealias ZipNOutput = ZipOutput<Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?>
typealias ZipNOutputC<Common> = ZipOutput<Common, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?>

fun <Common> Iterable<IStream.Many<Common>>.zipRepeating(): IStream.Many<List<Common>> {
    // TODO Performance could be improved by emitting zip elements earlier without first collecting each stream into a list
    return IStream.zip(this.map { it.toList().map { it.asSequence() } }) {
        CombiningSequence(it.toTypedArray())
    }.flatMapIterable { it.asIterable() }
}

class CombiningSequence<Common>(private val sequences: Array<Sequence<Common>>) : Sequence<List<Common>> {
    override fun iterator(): Iterator<List<Common>> = object : Iterator<List<Common>> {
        var initialized = false
        val lastValues = Array<Any?>(sequences.size) { UNINITIALIZED }
        val iterators = sequences.map { it.iterator() }.toTypedArray()
        override fun next(): List<Common> {
            for (i in sequences.indices) {
                if (iterators[i].hasNext()) lastValues[i] = iterators[i].next()
            }
            initialized = true
            return lastValues.map { it as Common }
        }

        override fun hasNext(): Boolean {
            return if (initialized) iterators.any { it.hasNext() } else iterators.all { it.hasNext() }
        }
    }
    object UNINITIALIZED
}

class ZipOutputSerializer<CommonT, Out : IZipOutput<CommonT>>(
    val elementSerializers: Array<KSerializer<IStepOutput<CommonT>>>,
) : KSerializer<ZipStepOutput<Out, CommonT>> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): ZipStepOutput<Out, CommonT> {
        if (elementSerializers.size == 1) {
            return ZipStepOutput(listOf(elementSerializers.single().deserialize(decoder)))
        } else {
            val values = Array<IStepOutput<CommonT>?>(elementSerializers.size) { null }
            decoder.decodeStructure(descriptor) {
                if (decodeSequentially()) {
                    for (i in elementSerializers.indices) {
                        values[i] = decodeSerializableElement(descriptor, i, elementSerializers[i])
                    }
                } else {
                    while (true) {
                        val i = decodeElementIndex(descriptor)
                        if (i == CompositeDecoder.DECODE_DONE) break
                        values[i] = decodeSerializableElement(descriptor, i, elementSerializers[i])
                    }
                }
            }
            return ZipStepOutput(values.map { it as IStepOutput<CommonT> })
        }
    }

    override val descriptor: SerialDescriptor = if (elementSerializers.size == 1) {
        elementSerializers.single().descriptor
    } else {
        val typeParameters = elementSerializers.map { it.descriptor }.toTypedArray()
        @OptIn(InternalSerializationApi::class)
        buildSerialDescriptor("modelix.zipN", StructureKind.LIST, *typeParameters) {
            elementSerializers.forEachIndexed { index, elementSerializer ->
                element(index.toString(), elementSerializer.descriptor)
            }
        }
    }

    override fun serialize(encoder: Encoder, value: ZipStepOutput<Out, CommonT>) {
        if (elementSerializers.size == 1) {
            elementSerializers.single().serialize(encoder, value.values.single())
        } else {
            encoder.encodeCollection(descriptor, elementSerializers.size) {
                value.values.forEachIndexed { index, elementValue ->
                    encodeSerializableElement(elementSerializers[index].descriptor, index, elementSerializers[index], elementValue)
                }
            }
        }
    }
}

data class ZipStepOutput<E : IZipOutput<Common>, Common>(val values: List<IStepOutput<Common>>) : IStepOutput<E> {
    override val value: E
        get() = ZipNOutput(values.map { it.value }) as E
}

fun <Common, E1, E2> IStepOutput<IZip2Output<Common, E1, E2>>.upcast() = this as ZipStepOutput<IZip2Output<Common, E1, E2>, Common>

interface IZipOutput<out Common> { val values: List<Common> }
interface IZip1Output<out Common, out E1> : IZipOutput<Common> { val first: E1 }
interface IZip2Output<out Common, out E1, out E2> : IZip1Output<Common, E1> { val second: E2 }
interface IZip3Output<out Common, out E1, out E2, out E3> : IZip2Output<Common, E1, E2> { val third: E3 }
interface IZip4Output<out Common, out E1, out E2, out E3, out E4> : IZip3Output<Common, E1, E2, E3> {
    val fourth: E4

    @Deprecated("Use fourth, the version without typo", ReplaceWith("fourth"))
    val forth
        get() = fourth
}
interface IZip5Output<out Common, out E1, out E2, out E3, out E4, out E5> : IZip4Output<Common, E1, E2, E3, E4> { val fifth: E5 }
interface IZip6Output<out Common, out E1, out E2, out E3, out E4, out E5, out E6> : IZip5Output<Common, E1, E2, E3, E4, E5> { val sixth: E6 }
interface IZip7Output<out Common, out E1, out E2, out E3, out E4, out E5, out E6, out E7> : IZip6Output<Common, E1, E2, E3, E4, E5, E6> { val seventh: E7 }
interface IZip8Output<out Common, out E1, out E2, out E3, out E4, out E5, out E6, out E7, out E8> : IZip7Output<Common, E1, E2, E3, E4, E5, E6, E7> { val eighth: E8 }
interface IZip9Output<out Common, out E1, out E2, out E3, out E4, out E5, out E6, out E7, out E8, out E9> : IZip8Output<Common, E1, E2, E3, E4, E5, E6, E7, E8> { val ninth: E9 }

operator fun <T> IZip1Output<*, T>.component1() = first
operator fun <T> IZip2Output<*, *, T>.component2() = second
operator fun <T> IZip3Output<*, *, *, T>.component3() = third
operator fun <T> IZip4Output<*, *, *, *, T>.component4() = fourth
operator fun <T> IZip5Output<*, *, *, *, *, T>.component5() = fifth
operator fun <T> IZip6Output<*, *, *, *, *, *, T>.component6() = sixth
operator fun <T> IZip7Output<*, *, *, *, *, *, *, T>.component7() = seventh
operator fun <T> IZip8Output<*, *, *, *, *, *, *, *, T>.component8() = eighth
operator fun <T> IZip9Output<*, *, *, *, *, *, *, *, *, T>.component9() = ninth

@Serializable
@SerialName("modelix.modelql.zip.output")
data class ZipOutput<out Common, out E1, out E2, out E3, out E4, out E5, out E6, out E7, out E8, out E9>(override val values: List<Common>) : IZip9Output<Common, E1, E2, E3, E4, E5, E6, E7, E8, E9> {
    operator fun get(index: Int): Common = values[index]
    override val first: E1 get() = values[0] as E1
    override val second: E2 get() = values[1] as E2
    override val third: E3 get() = values[2] as E3
    override val fourth: E4 get() = values[3] as E4
    override val fifth: E5 get() = values[4] as E5
    override val sixth: E6 get() = values[5] as E6
    override val seventh: E7 get() = values[6] as E7
    override val eighth: E8 get() = values[7] as E8
    override val ninth: E9 get() = values[8] as E9
}

fun <Common, T1 : Common, T2 : Common> IProducingStep<T1>.zip(other2: IProducingStep<T2>): IFluxStep<IZip2Output<Common, T1, T2>> {
    return ZipStep<Common, ZipOutput<Common, T1, T2, Unit, Unit, Unit, Unit, Unit, Unit, Unit>>().also {
        it.connect(this)
        it.connect(other2)
    }
}
fun <Common, T1 : Common, T2 : Common> IMonoStep<T1>.zip(other2: IMonoStep<T2>): IMonoStep<IZip2Output<Common, T1, T2>> {
    return ZipStep<Common, ZipOutput<Common, T1, T2, Unit, Unit, Unit, Unit, Unit, Unit, Unit>>().also {
        it.connect(this)
        it.connect(other2)
    }
}

fun <Common, T1 : Common, T2 : Common, T3 : Common> IProducingStep<T1>.zip(other2: IProducingStep<T2>, other3: IProducingStep<T3>): IFluxStep<IZip3Output<Common, T1, T2, T3>> {
    return ZipStep<Common, ZipOutput<Common, T1, T2, T3, Unit, Unit, Unit, Unit, Unit, Unit>>().also {
        it.connect(this)
        it.connect(other2)
        it.connect(other3)
    }
}
fun <Common, T1 : Common, T2 : Common, T3 : Common> IMonoStep<T1>.zip(other2: IMonoStep<T2>, other3: IMonoStep<T3>): IMonoStep<IZip3Output<Common, T1, T2, T3>> {
    return ZipStep<Common, ZipOutput<Common, T1, T2, T3, Unit, Unit, Unit, Unit, Unit, Unit>>().also {
        it.connect(this)
        it.connect(other2)
        it.connect(other3)
    }
}

fun <Common, T1 : Common, T2 : Common, T3 : Common, T4 : Common> IProducingStep<T1>.zip(other2: IProducingStep<T2>, other3: IProducingStep<T3>, other4: IProducingStep<T4>): IFluxStep<IZip4Output<Common, T1, T2, T3, T4>> {
    return ZipStep<Common, ZipOutput<Common, T1, T2, T3, T4, Unit, Unit, Unit, Unit, Unit>>().also {
        it.connect(this)
        it.connect(other2)
        it.connect(other3)
        it.connect(other4)
    }
}
fun <Common, T1 : Common, T2 : Common, T3 : Common, T4 : Common> IMonoStep<T1>.zip(other2: IMonoStep<T2>, other3: IMonoStep<T3>, other4: IMonoStep<T4>): IMonoStep<IZip4Output<Common, T1, T2, T3, T4>> {
    return ZipStep<Common, ZipOutput<Common, T1, T2, T3, T4, Unit, Unit, Unit, Unit, Unit>>().also {
        it.connect(this)
        it.connect(other2)
        it.connect(other3)
        it.connect(other4)
    }
}

fun <Common, T1 : Common, T2 : Common, T3 : Common, T4 : Common, T5 : Common> IProducingStep<T1>.zip(other2: IProducingStep<T2>, other3: IProducingStep<T3>, other4: IProducingStep<T4>, other5: IProducingStep<T5>): IFluxStep<IZip5Output<Common, T1, T2, T3, T4, T5>> {
    return ZipStep<Common, ZipOutput<Common, T1, T2, T3, T4, T5, Unit, Unit, Unit, Unit>>().also {
        it.connect(this)
        it.connect(other2)
        it.connect(other3)
        it.connect(other4)
        it.connect(other5)
    }
}
fun <Common, T1 : Common, T2 : Common, T3 : Common, T4 : Common, T5 : Common> IMonoStep<T1>.zip(other2: IMonoStep<T2>, other3: IMonoStep<T3>, other4: IMonoStep<T4>, other5: IMonoStep<T5>): IMonoStep<IZip5Output<Common, T1, T2, T3, T4, T5>> {
    return ZipStep<Common, ZipOutput<Common, T1, T2, T3, T4, T5, Unit, Unit, Unit, Unit>>().also {
        it.connect(this)
        it.connect(other2)
        it.connect(other3)
        it.connect(other4)
        it.connect(other5)
    }
}

fun <T> IProducingStep<T>.zip(vararg others: IProducingStep<T>): IFluxStep<IZipOutput<T>> = zipN(*others)
fun <T> IMonoStep<T>.zip(vararg others: IMonoStep<T>): IMonoStep<IZipOutput<T>> = zipN(*others)
fun <T> IProducingStep<T>.zipN(vararg others: IProducingStep<T>): IFluxStep<IZipOutput<T>> {
    return ZipStep<T, ZipOutput<T, Unit, Unit, Unit, Unit, Unit, Unit, Unit, Unit, Unit>>().also {
        it.connect(this)
        for (other in others) {
            it.connect(other)
        }
    }
}
fun <T> IMonoStep<T>.zipN(vararg others: IMonoStep<T>): IMonoStep<IZipOutput<T>> {
    return ZipStep<T, ZipOutput<T, Unit, Unit, Unit, Unit, Unit, Unit, Unit, Unit, Unit>>().also {
        it.connect(this)
        for (other in others) {
            it.connect(other)
        }
    }
}
fun <T> zipList(vararg steps: IMonoStep<T>): IMonoStep<IZipOutput<T>> {
    return ZipStep<T, ZipOutput<T, Unit, Unit, Unit, Unit, Unit, Unit, Unit, Unit, Unit>>().also {
        for (other in steps) {
            it.connect(other)
        }
    }
}
