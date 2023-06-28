package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeCollection
import kotlinx.serialization.modules.SerializersModule

open class ZipStep<CommonIn, Out : ZipOutput<CommonIn, *, *, *, *, *, *, *, *, *>>() : ProducingStep<Out>(), IConsumingStep<CommonIn>, IMonoStep<Out>, IFluxStep<Out> {
    private val producers = ArrayList<IProducingStep<CommonIn>>()

    override fun canBeEmpty(): Boolean = producers.any { it.canBeEmpty() }

    override fun canBeMultiple(): Boolean = producers.any { it.canBeMultiple() }

    override fun requiresSingularQueryInput(): Boolean {
        if (producers.any { !it.isSingle() }) return true
        return super<ProducingStep>.requiresSingularQueryInput()
    }

    override fun validate() {
        super<ProducingStep>.validate()
        for (producer in producers) {
            if (producer.canBeEmpty() && !producer.canBeMultiple()) {
                throw RuntimeException("optional mono step can prevent any output: $producer of $this")
            }
        }
    }

    override fun toString(): String {
        return "zip(${getProducers().joinToString(", ") { it.toString() }})"
    }

    override fun addProducer(producer: IProducingStep<CommonIn>) {
        if (getProducers().contains(producer)) return
        producers.add(producer)
        producer.addConsumer(this)
    }

    override fun getProducers(): List<IProducingStep<CommonIn>> {
        return producers
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("zip")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return ZipStep<Any?, ZipOutput<Any?, *, *, *, *, *, *, *, *, *>>()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Out> {
        val elementSerializers: Array<KSerializer<CommonIn>> = producers.map {
            it.getOutputSerializer(serializersModule) as KSerializer<CommonIn>
        }.toTypedArray()
        return ZipOutputSerializer<CommonIn>(elementSerializers) as KSerializer<Out>
    }

    override fun createFlow(context: IFlowInstantiationContext): Flow<Out> {
        return combine<Any?, Out>(producers.map { context.getOrCreateFlow(it) }) { values ->
            ZipNOutput(values.toList()) as Out
        }
    }

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<Out> {
        return CombiningSequence(producers.map { it.createSequence(queryInput) }.toTypedArray()) as Sequence<Out>
    }
}

typealias ZipNOutput = ZipOutput<Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?>

private class CombiningSequence(private val sequences: Array<Sequence<Any?>>) : Sequence<ZipNOutput> {
    override fun iterator(): Iterator<ZipNOutput> = object : Iterator<ZipNOutput> {
        var initialized = false
        val lastValues = Array<Any?>(sequences.size) { UNINITIALIZED }
        val iterators = sequences.map { it.iterator() }.toTypedArray()
        override fun next(): ZipNOutput {
            for (i in sequences.indices) {
                if (iterators[i].hasNext()) lastValues[i] = iterators[i].next()
            }
            initialized = true
            return ZipNOutput(lastValues.toList())
        }

        override fun hasNext(): Boolean {
            return if (initialized) iterators.any { it.hasNext() } else iterators.all { it.hasNext() }
        }
    }
    object UNINITIALIZED
}

class ZipOutputSerializer<CommonT>(val elementSerializers: Array<KSerializer<CommonT>>) : KSerializer<ZipOutput<CommonT, *, *, *, *, *, *, *, *, *>> {
    @OptIn(ExperimentalSerializationApi::class)
    override fun deserialize(decoder: Decoder): ZipOutput<CommonT, *, *, *, *, *, *, *, *, *> {
        val values = Array<Any?>(elementSerializers.size) { null }
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
        return ZipOutput<Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?>(values.toList()) as ZipOutput<CommonT, *, *, *, *, *, *, *, *, *>
    }

    override val descriptor: SerialDescriptor = ZipNOutputDesc(elementSerializers.map { it.descriptor }.toTypedArray())

    override fun serialize(encoder: Encoder, value: ZipOutput<CommonT, *, *, *, *, *, *, *, *, *>) {
        encoder.encodeCollection(descriptor, elementSerializers.size) {
            value.values.forEachIndexed { index, elementValue ->
                encodeSerializableElement(elementSerializers[index].descriptor, index, elementSerializers[index], elementValue)
            }
        }
    }
}

internal class ZipNOutputDesc(val elementDesc: Array<SerialDescriptor>) : SerialDescriptor {
    @ExperimentalSerializationApi
    override val elementsCount: Int
        get() = elementDesc.size

    @ExperimentalSerializationApi
    override val kind: SerialKind
        get() = StructureKind.LIST

    @ExperimentalSerializationApi
    override val serialName: String
        get() = "modelix.zipN"

    @ExperimentalSerializationApi
    override fun getElementAnnotations(index: Int): List<Annotation> = emptyList()

    @ExperimentalSerializationApi
    override fun getElementDescriptor(index: Int): SerialDescriptor = elementDesc[index]

    @ExperimentalSerializationApi
    override fun getElementIndex(name: String): Int {
        return name.toIntOrNull() ?: throw IllegalArgumentException("$name is not a valid list index")
    }

    @ExperimentalSerializationApi
    override fun getElementName(index: Int): String {
        return index.toString()
    }

    @ExperimentalSerializationApi
    override fun isElementOptional(index: Int): Boolean = false
}

interface IZipOutput<out Common> { val values: List<Common> }
interface IZip1Output<out Common, out E1> : IZipOutput<Common> { val first: E1 }
interface IZip2Output<out Common, out E1, out E2> : IZip1Output<Common, E1> { val second: E2 }
interface IZip3Output<out Common, out E1, out E2, out E3> : IZip2Output<Common, E1, E2> { val third: E3 }
interface IZip4Output<out Common, out E1, out E2, out E3, out E4> : IZip3Output<Common, E1, E2, E3> { val forth: E4 }
interface IZip5Output<out Common, out E1, out E2, out E3, out E4, out E5> : IZip4Output<Common, E1, E2, E3, E4> { val fifth: E5 }
interface IZip6Output<out Common, out E1, out E2, out E3, out E4, out E5, out E6> : IZip5Output<Common, E1, E2, E3, E4, E5> { val sixth: E6 }
interface IZip7Output<out Common, out E1, out E2, out E3, out E4, out E5, out E6, out E7> : IZip6Output<Common, E1, E2, E3, E4, E5, E6> { val seventh: E7 }
interface IZip8Output<out Common, out E1, out E2, out E3, out E4, out E5, out E6, out E7, out E8> : IZip7Output<Common, E1, E2, E3, E4, E5, E6, E7> { val eighth: E8 }
interface IZip9Output<out Common, out E1, out E2, out E3, out E4, out E5, out E6, out E7, out E8, out E9> : IZip8Output<Common, E1, E2, E3, E4, E5, E6, E7, E8> { val ninth: E9 }

@Serializable
@SerialName("modelix.modelql.zip.output")
data class ZipOutput<out Common, out E1, out E2, out E3, out E4, out E5, out E6, out E7, out E8, out E9>(override val values: List<Common>) : IZip9Output<Common, E1, E2, E3, E4, E5, E6, E7, E8, E9> {
    operator fun get(index: Int): Common = values[index]
    override val first: E1 get() = values[0] as E1
    override val second: E2 get() = values[1] as E2
    override val third: E3 get() = values[2] as E3
    override val forth: E4 get() = values[3] as E4
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
