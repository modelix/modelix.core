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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlin.experimental.ExperimentalTypeInference

class WhenStep<In, Out>(
    val cases: List<Pair<IMonoUnboundQuery<In, Boolean?>, IMonoUnboundQuery<In, Out>>>,
    val elseCase: IMonoUnboundQuery<In, Out>?
) : MonoTransformingStep<In, Out>() {

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

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out Out> {
        TODO("Not yet implemented")
    }

    override fun transform(input: In): Out {
        throw UnsupportedOperationException()
    }

    override fun createFlow(input: Flow<In>, context: IFlowInstantiationContext): Flow<Out> {
        return input.flatMapConcat {
            for (case in cases) {
                if (case.first.evaluate(it).presentAndEqual(true)) {
                    return@flatMapConcat case.second.asFlow(it)
                }
            }
            return@flatMapConcat elseCase?.asFlow(it) ?: emptyFlow<Out>()
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
