package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single
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

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("zip")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ZipStep<Any?, ZipNOutput>()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out>> {
        val elementSerializers: Array<KSerializer<IStepOutput<CommonIn>>> = producers.map {
            it.getOutputSerializer(serializersModule).upcast()
        }.toTypedArray()
        return ZipOutputSerializer(elementSerializers)
    }

    private fun ZipNOutputC<CommonIn>.upcast(): Out = this as Out

    override fun createFlow(context: IFlowInstantiationContext): Flow<ZipStepOutput<Out, CommonIn>> {
        val inputFlows = producers.map {
            val possiblyEmptyFlow = context.getOrCreateFlow(it)
            if (it is AllowEmptyStep) {
                possiblyEmptyFlow
            } else {
                possiblyEmptyFlow.assertNotEmpty { producers.toString() }
            }
        }

        // optimization if all inputs are mono steps
        if (producers.all { it.isSingle() }) {
            return flow {
                emit((ZipStepOutput(inputFlows.map { it.single() })))
            }
        }

        // optimization for a pair of flux and mono inputs
        if (producers.size == 2) {
            if (producers[0].isSingle()) {
                return flow {
                    val value0 = inputFlows[0].single()
                    inputFlows[1].collect { value1 ->
                        emit((ZipStepOutput(listOf(value0, value1))))
                    }
                }
            } else if (producers[1].isSingle()) {
                return flow {
                    val value1 = inputFlows[1].single()
                    inputFlows[0].collect { value0 ->
                        emit((ZipStepOutput(listOf(value0, value1))))
                    }
                }
            }
        }

        // TODO this might be slower, but combine seems to be buggy (elements get lost)
        return flow {
            emitAll((CombiningSequence(inputFlows.map { it.asSequence() }.toTypedArray())).map { ZipStepOutput<Out, CommonIn>(it.values) }.asFlow())
        }
//        return combine<Any?, Out>(inputFlows) { values ->
//            ZipNOutput(values.toList()) as Out
//        }
    }

    override fun createSequence(evaluationContext: QueryEvaluationContext, queryInput: Sequence<Any?>): Sequence<Out> {
        return CombiningSequence(producers.map { it.createSequence(evaluationContext, queryInput) }.toTypedArray()).map { it.upcast() }
    }
}

class AllowEmptyStep<E>() : IdentityStep<E>() {
    override fun toString(): String {
        return "${getProducer()}.allowEmpty()"
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
    }
}
fun <T> IFluxStep<T>.allowEmpty(): IFluxStep<T> = AllowEmptyStep<T>().also { connect(it) }
fun <T> IMonoStep<T>.allowEmpty(): IMonoStep<T> = AllowEmptyStep<T>().also { connect(it) }

class AssertNotEmptyStep<E>() : IdentityStep<E>() {
    override fun canBeEmpty(): Boolean {
        return false
    }

    override fun createFlow(input: StepFlow<E>, context: IFlowInstantiationContext): StepFlow<E> {
        return input.assertNotEmpty()
    }

    override fun toString(): String {
        return "${getProducer()}.assertNotEmpty()"
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
    }
}

fun <T> IFluxStep<T>.assertNotEmpty(): IFluxStep<T> = AssertNotEmptyStep<T>().also { connect(it) }
fun <T> IMonoStep<T>.assertNotEmpty(): IMonoStep<T> = AssertNotEmptyStep<T>().also { connect(it) }

typealias ZipNOutput = ZipOutput<Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?>
typealias ZipNOutputC<Common> = ZipOutput<Common, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?>

class CombiningSequence<Common>(private val sequences: Array<Sequence<Common>>) : Sequence<ZipNOutputC<Common>> {
    override fun iterator(): Iterator<ZipNOutputC<Common>> = object : Iterator<ZipNOutputC<Common>> {
        var initialized = false
        val lastValues = Array<Any?>(sequences.size) { UNINITIALIZED }
        val iterators = sequences.map { it.iterator() }.toTypedArray()
        override fun next(): ZipNOutputC<Common> {
            for (i in sequences.indices) {
                if (iterators[i].hasNext()) lastValues[i] = iterators[i].next()
            }
            initialized = true
            return ZipNOutputC<Common>(lastValues.map { it as Common })
        }

        override fun hasNext(): Boolean {
            return if (initialized) iterators.any { it.hasNext() } else iterators.all { it.hasNext() }
        }
    }
    object UNINITIALIZED
}

class ZipOutputSerializer<CommonT, Out : IZipOutput<CommonT>>(
    val elementSerializers: Array<KSerializer<IStepOutput<CommonT>>>
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
        ZipNOutputDesc(elementSerializers.map { it.descriptor }.toTypedArray())
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

class ZipStepOutput<E : IZipOutput<Common>, Common>(val values: List<IStepOutput<Common>>) : IStepOutput<E> {
    override val value: E
        get() = ZipNOutput(values.map { it.value }) as E
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
