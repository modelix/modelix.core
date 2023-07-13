/*
 * Copyright 2003-2023 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.modelql.core

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.modules.SerializersModule
import kotlin.experimental.ExperimentalTypeInference

class WhenStep<In, Out>(
    val cases: List<Pair<IMonoUnboundQuery<In, Boolean?>, IMonoUnboundQuery<In, Out>>>,
    val elseCase: IMonoUnboundQuery<In, Out>?
) : MonoTransformingStep<In, Out>() {

    override fun toString(): String {
        return "when()" + cases.joinToString("") { ".if(${it.first}).then(${it.second})" } + ".else($elseCase)"
    }

    override fun canBeEmpty(): Boolean {
        if (elseCase == null) return true
        if (getProducer().canBeEmpty()) return true
        return cases.any { it.second.canBeEmpty() }
    }

    override fun canBeMultiple(): Boolean {
        return false
    }

    override fun createDescriptor(context: QuerySerializationContext): StepDescriptor {
        return Descriptor(
            cases.map { it.first.castToInstance().createDescriptor(context) to it.second.castToInstance().createDescriptor(context) },
            elseCase?.castToInstance()?.createDescriptor(context)
        )
    }

    @Serializable
    @SerialName("when")
    class Descriptor(val cases: List<Pair<QueryDescriptor, QueryDescriptor>>, val elseCase: QueryDescriptor? = null) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return WhenStep<Any?, Any?>(
                cases.map { it.first.createQuery(context) as MonoUnboundQuery<Any?, Boolean?> to it.second.createQuery(context) as MonoUnboundQuery<Any?, Any?> },
                elseCase?.let { it.createQuery(context) as MonoUnboundQuery<Any?, Any?> }
            )
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out>> {
        return MultiplexedOutputSerializer<Out>(
            cases.map { it.second.getAggregationOutputSerializer(serializersModule).upcast() } +
                listOfNotNull(elseCase?.getAggregationOutputSerializer(serializersModule)?.upcast())
        )
    }

    override fun transform(input: In): Out {
        throw UnsupportedOperationException()
    }

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        return input.flatMapConcat {
            for ((index, case) in cases.withIndex()) {
                if (case.first.evaluate(it.value).presentAndEqual(true)) {
                    return@flatMapConcat case.second.asFlow(it).map { MultiplexedOutput(index, it) }
                }
            }
            val elseCaseIndex = cases.size
            return@flatMapConcat elseCase?.asFlow(it)?.map { MultiplexedOutput(elseCaseIndex, it) }
                ?: emptyFlow<Out>().asStepFlow()
        }
    }

    override fun createTransformingSequence(input: Sequence<In>): Sequence<Out> {
        return input.flatMap {
            for (case in cases) {
                if (case.first.evaluate(it).presentAndEqual(true)) {
                    return@flatMap case.second.asSequence(sequenceOf(it))
                }
            }
            return@flatMap elseCase?.asSequence(sequenceOf(it)) ?: emptySequence()
        }
    }

    override fun evaluate(queryInput: Any?): Optional<Out> {
        return createSequence(sequenceOf(queryInput))
            .map { Optional.of(it) }
            .ifEmpty { sequenceOf(Optional.empty<Out>()) }
            .first()
    }
}

@Serializable
class MultiplexedOutput<out E>(val muxIndex: Int, val output: IStepOutput<E>) : IStepOutput<E> {
    override val value: E
        get() = output.value
}

data class MultiplexedOutputSerializer<E>(
    val serializers: List<KSerializer<IStepOutput<E>>>
) : KSerializer<MultiplexedOutput<E>> {
    override fun deserialize(decoder: Decoder): MultiplexedOutput<E> {
        var muxIndex: Int? = null
        var caseOutput: IStepOutput<E>? = null

        decoder.decodeStructure(descriptor) {
            while (true) {
                val i = decodeElementIndex(descriptor)
                if (i == CompositeDecoder.DECODE_DONE) break
                when (i) {
                    0 -> muxIndex = decodeIntElement(descriptor, i)
                    1 -> {
                        val caseSerializer =
                            getCaseSerializer(muxIndex ?: throw IllegalStateException("muxIndex expected first"))
                        caseOutput = decodeSerializableElement(descriptor, i, caseSerializer)
                    }
                    else -> throw RuntimeException("Unexpected element $i")
                }
            }
        }
        return MultiplexedOutput(
            muxIndex ?: throw IllegalStateException("muxIndex missing"),
            caseOutput ?: throw IllegalStateException("output missing")
        )
    }

    private fun getCaseSerializer(caseIndex: Int) = serializers[caseIndex]

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("whenStepOutput") {
        element("muxIndex", indexSerializer.descriptor, isOptional = false)
        element("output", dummySerializer.descriptor, isOptional = false)
    }

    override fun serialize(encoder: Encoder, value: MultiplexedOutput<E>) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.muxIndex)
            encodeSerializableElement(descriptor, 1, getCaseSerializer(value.muxIndex), value.output)
        }
    }

    companion object {
        private val indexSerializer = Int.serializer()
        private val dummySerializer = NothingSerializer()
    }
}

@OptIn(ExperimentalTypeInference::class)
class WhenStepBuilder<In, Out>() {
    private val cases = ArrayList<Pair<IMonoUnboundQuery<In, Boolean?>, IMonoUnboundQuery<In, Out>>>()

    fun `if`(condition: (IMonoStep<In>) -> IMonoStep<Boolean?>): CaseBuilder {
        return CaseBuilder(IUnboundQuery.buildMono(condition))
    }

    @BuilderInference
    fun `else`(body: (IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
        return WhenStep(cases, IUnboundQuery.buildMono(body))
    }

    inner class CaseBuilder(val condition: IMonoUnboundQuery<In, Boolean?>) {
        @BuilderInference
        fun then(body: (IMonoStep<In>) -> IMonoStep<Out>): WhenStepBuilder<In, Out> {
            cases += condition to IUnboundQuery.buildMono(body)
            return this@WhenStepBuilder
        }
    }
}

fun <In, Out> IMonoStep<In>.`when`() = WhenStepBuilder<In, Out>()
