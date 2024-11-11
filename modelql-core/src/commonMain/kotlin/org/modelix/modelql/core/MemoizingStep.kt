/*
 * Copyright (c) 2024.
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

import com.badoo.reaktive.observable.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MemoizingStep<In, Out>(val query: MonoUnboundQuery<In, Out>) : MonoTransformingStep<In, Out>() {

    override fun validate() {
        super.validate()
        check(query.outputStep != this)
    }

    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty() || query.outputStep.canBeEmpty()

    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple() || query.outputStep.canBeMultiple()

    override fun requiresSingularQueryInput(): Boolean {
        return super.requiresSingularQueryInput() || query.inputStep.requiresSingularQueryInput()
    }

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        val memoizer = IMemoizationPersistence.CONTEXT_INSTANCE.getValue().getMemoizer(query)
        return input.map {
            memoizer.memoize(it)
        }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        return query.getAggregationOutputSerializer(serializationContext + (query.inputStep to getProducer().getOutputSerializer(serializationContext)))
    }

    override fun toString(): String {
        return "${getProducer()}\n.memoize {\n${query.toString().prependIndent("  ")}\n}"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(query))

    @Serializable
    @SerialName("memoize")
    data class Descriptor(val queryId: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MemoizingStep<Any?, Any?>(context.getOrCreateQuery(queryId) as MonoUnboundQuery<Any?, Any?>)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(idReassignments.reassign(queryId))

        override fun prepareNormalization(idReassignments: IdReassignments) {
            idReassignments.visitQuery(queryId)
        }
    }
}

fun <In, Out> IFluxStep<In>.memoize(body: IMonoQueryBuilderContext<In, Out>.(IMonoStep<In>) -> IMonoStep<Out>): IFluxStep<Out> {
    return MemoizingStep(buildMonoQuery { body(it) }.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IMonoStep<In>.memoize(body: IMonoQueryBuilderContext<In, Out>.(IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out> {
    return MemoizingStep(buildMonoQuery { body(it) }.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IMonoStep<In>.memoize(query: IMonoUnboundQuery<In, Out>): IMonoStep<Out> {
    return MemoizingStep(query.castToInstance()).connectAndDowncast(this)
}
fun <In, Out> IFluxStep<In>.memoize(query: IMonoUnboundQuery<In, Out>): IFluxStep<Out> {
    return MemoizingStep(query.castToInstance()).connectAndDowncast(this)
}

fun <In, Out> IMonoStep<In>.memoizeFlux(body: (IMonoStep<In>) -> IFluxStep<Out>): IFluxStep<Out> {
    return memoize { body(it).toList() }.toFlux()
}

fun <In, K, Out : Any> IMonoStep<In>.find(elements: (IMonoStep<In>) -> IFluxStep<Out>, keySelector: (IMonoStep<Out>) -> IMonoStep<K>, key: IMonoStep<K>): IMonoStep<Out> {
    return memoize { elements(it).associateBy(keySelector) }.get(key).filterNotNull()
}

fun <In, K, Out : Any> IMonoStep<In>.findAll(elements: (IMonoStep<In>) -> IFluxStep<Out>, keySelector: (IMonoStep<Out>) -> IProducingStep<K>, key: IMonoStep<K>): IFluxStep<Out> {
    return memoize { elements(it).flatMap { keySelector(it).allowEmpty().zip(it) }.toMultimap() }.get(key).filterNotNull().toFlux()
}

fun <In, K, Out : Any> IMonoStep<In>.findAll(elements: (IMonoStep<In>) -> IFluxStep<Out>, keySelector: (IMonoStep<Out>) -> IProducingStep<K>, keys: IFluxStep<K>): IFluxStep<Out> {
    val map: IMonoStep<Map<K, List<Out>>> = memoize { elements(it).flatMap { keySelector(it).allowEmpty().zip(it) }.toMultimap() }
    return map.zip(keys.allowEmpty()).flatMap { it.first.get(it.second).filterNotNull().toFlux() }
}
