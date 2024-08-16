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

import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.asObservable
import com.badoo.reaktive.observable.map
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.asObservable
import com.badoo.reaktive.single.repeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.reflect.KType

interface IStep {
    val owner: QueryReference<*>
    fun validate() {}

    fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor = throw UnsupportedOperationException("${this::class} not serializable")

    fun requiresWriteAccess(): Boolean = false
    fun hasSideEffect(): Boolean = requiresWriteAccess()
    fun needsCoroutineScope() = false
    fun requiresSingularQueryInput(): Boolean

    fun getRootInputSteps(): Set<IStep> = if (this is IConsumingStep<*>) getProducers().flatMap { it.getRootInputSteps() }.toSet() else setOf(this)
}

interface IFlowInstantiationContext {
    val evaluationContext: QueryEvaluationContext
    fun <T> getOrCreateFlow(step: IProducingStep<T>): StepFlow<T>
    fun <T> getFlow(step: IProducingStep<T>): Observable<T>?
}
class FlowInstantiationContext(
    override var evaluationContext: QueryEvaluationContext,
    val query: UnboundQuery<*, *, *>,
) : IFlowInstantiationContext {
    private val createdProducers = HashMap<IProducingStep<*>, Observable<*>>()
    fun <T> put(step: IProducingStep<T>, producer: Observable<T>) {
        createdProducers[step] = producer
    }
    override fun <T> getOrCreateFlow(step: IProducingStep<T>): StepFlow<T> {
        if (evaluationContext.hasValue(step)) return evaluationContext.getValue(step).asObservable()
        return (createdProducers as MutableMap<IProducingStep<T>, StepFlow<T>>)
            .getOrPut(step) { step.createFlow(this) }
    }

    override fun <T> getFlow(step: IProducingStep<T>): Observable<T>? {
        return (createdProducers as MutableMap<IProducingStep<T>, Observable<T>>)[step]
    }
}

/**
 * The output serializer of a query is context dependent when used by multiple `QueryCallStep`s.
 * This class carries the correct serializer for the input of a query depending on from where it is called.
 */
class SerializationContext(val serializersModule: SerializersModule, val queryInputSerializers: Map<QueryInput<*>, KSerializer<IStepOutput<*>>> = emptyMap()) {
    operator fun <T> plus(queryInputSerializer: Pair<QueryInput<T>, KSerializer<out IStepOutput<T>>>): SerializationContext {
        return SerializationContext(
            serializersModule,
            queryInputSerializers + (queryInputSerializer.first to queryInputSerializer.second.upcast()),
        )
    }

    public inline fun <reified T> serializer(): KSerializer<T> {
        return serializersModule.serializer<T>()
    }

    public fun serializer(type: KType): KSerializer<Any?> = serializersModule.serializer(type)
}

interface IProducingStep<out E> : IStep {
    fun addConsumer(consumer: IConsumingStep<E>)
    fun getConsumers(): List<IConsumingStep<*>>
    fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>>
    fun createFlow(context: IFlowInstantiationContext): StepFlow<E>

    fun outputIsConsumedMultipleTimes(): Boolean {
        return getConsumers().size > 1 || getConsumers().any { it.inputIsConsumedMultipleTimes() }
    }

    fun canBeEmpty(): Boolean = true
    fun canBeMultiple(): Boolean = true
    fun canEvaluateStatically(): Boolean = false
    fun evaluateStatically(): E = throw UnsupportedOperationException()
    override fun requiresSingularQueryInput(): Boolean {
        return getConsumers().size > 1 || getConsumers().any { it.requiresSingularQueryInput() }
    }
}
fun IProducingStep<*>.isSingle(): Boolean = !canBeEmpty() && !canBeMultiple()

fun <T> IProducingStep<T>.connect(consumer: IConsumingStep<T>) {
    val producer: IProducingStep<T> = this
    if (consumer.owner != this.owner && producer !is SharedStep<*>) {
        val producerContext = QueryBuilderContext.CONTEXT_VALUE.getAllValues().find { it.queryReference == producer.owner }
        checkNotNull(producerContext) { "Step belongs to a different query that is already finalized: $producer" }
        producerContext.computeWith {
            with(producerContext) { producer.shared() }
        }.connect(consumer)
        return
    }
    addConsumer(consumer)
}
fun <T> IConsumingStep<T>.connect(producer: IProducingStep<T>) = producer.connect(this)

interface IMonoStep<out E> : IProducingStep<E>

interface IFluxStep<out E> : IProducingStep<E>
interface IFluxOrMonoStep<out E> : IMonoStep<E>, IFluxStep<E>

interface IConsumingStep<in E> : IStep {
    fun addProducer(producer: IProducingStep<E>)
    fun getProducers(): List<IProducingStep<*>>
    fun inputIsConsumedMultipleTimes(): Boolean = if (this is IProducingStep<*>) outputIsConsumedMultipleTimes() else false
}

interface IProcessingStep<In, Out> : IConsumingStep<In>, IProducingStep<Out> {
    override fun inputIsConsumedMultipleTimes(): Boolean {
        return outputIsConsumedMultipleTimes()
    }
}

abstract class ProducingStep<E> : IProducingStep<E> {
    override val owner: QueryReference<*> = QueryReference.CONTEXT_VALUE.getValue()
    private val consumers = ArrayList<IConsumingStep<E>>()

    override fun addConsumer(consumer: IConsumingStep<E>) {
        if (consumers.contains(consumer)) return
        if (consumer.owner != this.owner && this !is SharedStep<*>) {
            throw CrossQueryReferenceException("Cannot consume step from a different query (${consumer.owner.queryId} != ${this.owner.queryId}): $this")
        }
        consumers += consumer
        consumer.addProducer(this)
    }

    override fun getConsumers(): List<IConsumingStep<E>> {
        return consumers
    }
}

abstract class TransformingStep<In, Out> : IProcessingStep<In, Out>, ProducingStep<Out>() {

    private var producer: IProducingStep<In>? = null

    override fun validate() {
        super<ProducingStep>.validate()
        require(producer != null) { "Step has no input: ${this::class}" }
    }

    override fun addProducer(producer: IProducingStep<In>) {
        if (this.producer != null) throw IllegalStateException("Only one input supported")
        this.producer = producer
    }

    override fun getProducers(): List<IProducingStep<In>> {
        return listOfNotNull(producer)
    }

    fun getProducer(): IProducingStep<In> = producer!!

    protected abstract fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out>

    override fun createFlow(context: IFlowInstantiationContext): StepFlow<Out> {
        return createFlow(context.getOrCreateFlow(getProducer()), context)
    }
}

abstract class MonoTransformingStep<In, Out> : TransformingStep<In, Out>(), IMonoStep<Out>, IFluxStep<Out> {
    override fun canBeEmpty(): Boolean = getProducer().canBeEmpty()
    override fun canBeMultiple(): Boolean = getProducer().canBeMultiple()

    fun connectAndDowncast(producer: IMonoStep<In>): IMonoStep<Out> = also { producer.connect(it) }
    fun connectAndDowncast(producer: IFluxStep<In>): IFluxStep<Out> = also { producer.connect(it) }
}

abstract class SimpleMonoTransformingStep<In, Out> : MonoTransformingStep<In, Out>() {
    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        return input.map { transform(context.evaluationContext, it.value).asStepOutput(this) }
    }
    abstract fun transform(evaluationContext: QueryEvaluationContext, input: In): Out
}

abstract class FluxTransformingStep<In, Out> : TransformingStep<In, Out>(), IFluxStep<Out> {
    fun connectAndDowncast(producer: IProducingStep<In>): IFluxStep<Out> = also { producer.connect(it) }
}

abstract class AggregationStep<In, Out> : MonoTransformingStep<In, Out>() {
    fun connectAndDowncast(producer: IProducingStep<In>): IMonoStep<Out> = also { producer.connect(it) }

    override fun canBeEmpty(): Boolean = false

    override fun canBeMultiple(): Boolean = false
    override fun requiresSingularQueryInput(): Boolean = true

    override fun createFlow(input: StepFlow<In>, context: IFlowInstantiationContext): StepFlow<Out> {
        val aggregated = aggregate(input)
        return if (outputIsConsumedMultipleTimes()) aggregated.repeat() else aggregated.asObservable()
    }

    override fun needsCoroutineScope() = outputIsConsumedMultipleTimes()

    override fun inputIsConsumedMultipleTimes(): Boolean {
        return false
    }

    protected abstract fun aggregate(input: StepFlow<In>): Single<IStepOutput<Out>>
}
