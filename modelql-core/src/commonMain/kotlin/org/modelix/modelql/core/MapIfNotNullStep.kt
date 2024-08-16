/*
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

import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.observable.observableOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MapIfNotNullStep<In : Any, Out>(val query: MonoUnboundQuery<In, Out>) : MonoTransformingStep<In?, Out?>() {

    override fun requiresWriteAccess(): Boolean {
        return query.requiresWriteAccess()
    }

    override fun createFlow(input: StepFlow<In?>, context: IFlowInstantiationContext): StepFlow<Out?> {
        return input.flatMap { stepOutput ->
            stepOutput.value?.let { query.asFlow(context.evaluationContext, stepOutput.upcast()).map { MultiplexedOutput(1, it) } }
                ?: observableOf(MultiplexedOutput(0, stepOutput.upcast()))
        }
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out?>> {
        val inputSerializer: KSerializer<IStepOutput<In?>> = getProducer().getOutputSerializer(serializationContext).upcast()
        val mappedSerializer: KSerializer<out IStepOutput<Out>> = query.getElementOutputSerializer(
            serializationContext + (query.inputStep to inputSerializer as KSerializer<out IStepOutput<In>>),
        )
        val multiplexedSerializer: MultiplexedOutputSerializer<Out?> = MultiplexedOutputSerializer(
            this,
            listOf(
                inputSerializer.upcast() as KSerializer<IStepOutput<Out?>>,
                mappedSerializer.upcast() as KSerializer<IStepOutput<Out?>>,
            ),
        )
        return multiplexedSerializer
    }

    override fun toString(): String {
        return "${getProducer()}.mapIfNotNull { $query }"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(context.load(query))

    @Serializable
    @SerialName("mapIfNotNull")
    class Descriptor(val queryId: QueryId) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MapIfNotNullStep<Any, Any?>(context.getOrCreateQuery(queryId) as MonoUnboundQuery<Any, Any?>)
        }
    }
}

fun <In : Any, Out> IMonoStep<In?>.mapIfNotNull(body: IQueryBuilderContext<In, Out>.(IMonoStep<In>) -> IMonoStep<Out>): IMonoStep<Out?> {
    return MapIfNotNullStep(buildMonoQuery(body).castToInstance()).also { connect(it) }
}

fun <In : Any, Out> IFluxStep<In?>.mapIfNotNull(body: IQueryBuilderContext<In, Out>.(IMonoStep<In>) -> IMonoStep<Out>): IFluxStep<Out?> {
    return MapIfNotNullStep(buildMonoQuery(body).castToInstance()).also { connect(it) }
}
