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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeCollection
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
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

    override fun createDescriptor(): StepDescriptor {
        return Descriptor(
            cases.map { it.first.castToInstance().createDescriptor() to it.second.castToInstance().createDescriptor() },
            elseCase?.castToInstance()?.createDescriptor()
        )
    }

    @Serializable
    @SerialName("when")
    class Descriptor(val cases: List<Pair<QueryDescriptor, QueryDescriptor>>, val elseCase: QueryDescriptor? = null) : StepDescriptor() {
        override fun createStep(): IStep {
            return WhenStep<Any?, Any?>(
                cases.map { it.first.createQuery() as MonoUnboundQuery<Any?, Boolean?> to it.second.createQuery() as MonoUnboundQuery<Any?, Any?> },
                elseCase?.let { it.createQuery() as MonoUnboundQuery<Any?, Any?> }
            )
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out>> {
        return WhenStepOutputSerializer<Out>(
            cases.map { it.second.getOutputSerializer(serializersModule).upcast() },
            elseCase?.getOutputSerializer(serializersModule)?.upcast()
        )
    }

    override fun transform(input: In): Out {
        throw UnsupportedOperationException()
    }

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        return input.flatMapConcat {
            for ((index, case) in cases.withIndex()) {
                if (case.first.evaluate(it.value).presentAndEqual(true)) {
                    return@flatMapConcat case.second.asFlow(it).map { WhenStepOutput(index, it) }
                }
            }
            return@flatMapConcat elseCase?.asFlow(it)?.map { WhenStepOutput(-1, it) } ?: emptyFlow<Out>().asStepFlow()
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
class WhenStepOutput<E>(val caseIndex: Int, val caseOutput: IStepOutput<E>) : IStepOutput<E> {
    override val value: E
        get() = caseOutput.value
}

class WhenStepOutputSerializer<E>(
    val caseSerializers: List<KSerializer<IStepOutput<E>>>,
    val elseSerializer: KSerializer<IStepOutput<E>>?
) : KSerializer<WhenStepOutput<E>> {
    override fun deserialize(decoder: Decoder): WhenStepOutput<E> {
        var caseIndex: Int? = null
        var caseOutput: IStepOutput<E>? = null

        decoder.decodeStructure(descriptor) {
            while (true) {
                val i = decodeElementIndex(descriptor)
                if (i == CompositeDecoder.DECODE_DONE) break
                when (i) {
                    0 -> caseIndex = decodeIntElement(descriptor, i)
                    1 -> {
                        val caseSerializer =
                            getCaseSerializer(caseIndex ?: throw IllegalStateException("caseIndex expected first"))
                        caseOutput = decodeSerializableElement(descriptor, i, caseSerializer)
                    }
                    else -> throw RuntimeException("Unexpected element $i")
                }
            }
        }
        return WhenStepOutput(
            caseIndex ?: throw IllegalStateException("caseIndex missing"),
            caseOutput ?: throw IllegalStateException("caseOutput missing")
        )
    }

    private fun getCaseSerializer(caseIndex: Int) = caseIndex.let {
        when (it) {
            -1 -> elseSerializer!!
            else -> caseSerializers[it]
        }
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("whenStepOutput") {
        element("caseIndex", indexSerializer.descriptor, isOptional = false)
        element("caseOutput", dummySerializer.descriptor, isOptional = false)
    }

    override fun serialize(encoder: Encoder, value: WhenStepOutput<E>) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.caseIndex)
            encodeSerializableElement(descriptor, 1, getCaseSerializer(value.caseIndex), value.caseOutput)
        }
    }

    companion object {
        private val indexSerializer = Int.serializer()
        private val dummySerializer = Int.serializer()
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
