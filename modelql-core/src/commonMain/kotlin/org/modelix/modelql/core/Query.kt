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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

interface IQueryExecutor<out In> {
    fun <Out> createFlow(query: IUnboundQuery<In, *, Out>): StepFlow<Out>
}

class SimpleQueryExecutor<E>(val input: E) : IQueryExecutor<E> {
    override fun <ElementOut> createFlow(query: IUnboundQuery<E, *, ElementOut>): StepFlow<ElementOut> {
        return query.asFlow(QueryEvaluationContext.EMPTY, input.asStepOutput(null))
    }
}

interface IQuery<out AggregationOut, out ElementOut> {
    suspend fun execute(): IStepOutput<AggregationOut>
    suspend fun asFlow(): StepFlow<ElementOut>
}

interface IMonoQuery<out Out> : IQuery<Out, Out> {
    fun <T> map(body: (IMonoStep<Out>) -> IMonoStep<T>): IMonoQuery<T>
    fun <T> flatMap(body: (IMonoStep<Out>) -> IFluxStep<T>): IFluxQuery<T>
}

interface IFluxQuery<out Out> : IQuery<List<IStepOutput<Out>>, Out> {
    fun <T> flatMap(body: (IMonoStep<Out>) -> IFluxStep<T>): IFluxQuery<T>
    fun <T> map(body: (IMonoStep<Out>) -> IMonoStep<T>): IFluxQuery<T>
}

private abstract class BoundQuery<In, out AggregationOut, out ElementOut>(val executor: IQueryExecutor<In>) : IQuery<AggregationOut, ElementOut> {
    abstract val query: IUnboundQuery<In, AggregationOut, ElementOut>

    override suspend fun asFlow(): StepFlow<ElementOut> {
        return executor.createFlow(query)
    }

    override fun toString(): String {
        return "$executor -> $query"
    }
}

private class MonoBoundQuery<In, Out>(executor: IQueryExecutor<In>, override val query: MonoUnboundQuery<In, Out>) : BoundQuery<In, Out, Out>(executor), IMonoQuery<Out> {

    override suspend fun execute(): IStepOutput<Out> {
        try {
            return executor.createFlow(query).single()
        } catch (ex: NoSuchElementException) {
            throw RuntimeException("Empty query result: " + this, ex)
        }
    }

    override fun <T> map(body: (IMonoStep<Out>) -> IMonoStep<T>): IMonoQuery<T> {
        return query.map(body).bind(executor)
    }

    override fun <T> flatMap(body: (IMonoStep<Out>) -> IFluxStep<T>): IFluxQuery<T> {
        return query.flatMap(body).bind(executor)
    }
}

private class FluxBoundQuery<In, Out>(executor: IQueryExecutor<In>, override val query: FluxUnboundQuery<In, Out>) :
    BoundQuery<In, List<IStepOutput<Out>>, Out>(executor), IFluxQuery<Out> {

    override suspend fun execute(): IStepOutput<List<IStepOutput<Out>>> {
        return executor.createFlow(query).toList().asStepOutput(null)
    }

    override fun <T> flatMap(body: (IMonoStep<Out>) -> IFluxStep<T>): IFluxQuery<T> {
        return query.flatMap(body).bind(executor)
    }

    override fun <T> map(body: (IMonoStep<Out>) -> IMonoStep<T>): IFluxQuery<T> {
        return query.map(body).bind(executor)
    }
}

interface IUnboundQuery<in In, out AggregationOut, out ElementOut> {
    val reference: IQueryReference<IUnboundQuery<In, AggregationOut, ElementOut>>
    suspend fun execute(evaluationContext: QueryEvaluationContext, input: IStepOutput<In>): IStepOutput<AggregationOut>
    fun asFlow(evaluationContext: QueryEvaluationContext, input: StepFlow<In>): StepFlow<ElementOut>
    fun asFlow(evaluationContext: QueryEvaluationContext, input: IStepOutput<In>): StepFlow<ElementOut> = asFlow(evaluationContext, flowOf(input))

    fun requiresWriteAccess(): Boolean
    fun canBeEmpty(): Boolean

    fun bind(executor: IQueryExecutor<In>): IQuery<AggregationOut, ElementOut>
    fun getElementOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<ElementOut>>
    fun getAggregationOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<AggregationOut>>

    companion object {
        fun <In, Out> buildMono(body: (IMonoStep<In>) -> IMonoStep<Out>) = buildMonoQuery { body(it) }
        fun <In, Out> buildFlux(body: (IMonoStep<In>) -> IFluxStep<Out>) = buildFluxQuery { body(it) }
    }
}

interface IMonoUnboundQuery<in In, out Out> : IUnboundQuery<In, Out, Out> {
    override fun bind(executor: IQueryExecutor<In>): IMonoQuery<Out>
    fun <T> map(query: IMonoUnboundQuery<Out, T>): IMonoUnboundQuery<In, T>
    fun <T> map(query: IFluxUnboundQuery<Out, T>): IFluxUnboundQuery<In, T>
    fun <T> map(body: (IMonoStep<Out>) -> IMonoStep<T>): IMonoUnboundQuery<In, T> = map(buildMonoQuery { body(it) })
    fun <T> flatMap(body: (IMonoStep<Out>) -> IFluxStep<T>): IFluxUnboundQuery<In, T> = map(buildFluxQuery { body(it) })
}

suspend fun <In, Out> IMonoUnboundQuery<In, Out>.evaluate(evaluationContext: QueryEvaluationContext, input: In): Optional<Out> {
    return SimpleQueryExecutor(input)
        .createFlow(this)
        .map { Optional.of(it.value) }
        .onEmpty { Optional.empty<Out>() }
        .single()
}

fun <In, Out, AggregationT, T> IMonoUnboundQuery<In, Out>.map(query: IUnboundQuery<Out, AggregationT, T>): IUnboundQuery<In, AggregationT, T> {
    return when (query) {
        is IMonoUnboundQuery<*, *> -> map(query as IMonoUnboundQuery<Out, T>)
        is IFluxUnboundQuery<*, *> -> map(query as IFluxUnboundQuery<Out, T>)
        else -> throw RuntimeException("Unknown query type: $query")
    } as IUnboundQuery<In, AggregationT, T>
}

interface IFluxUnboundQuery<in In, out Out> : IUnboundQuery<In, List<IStepOutput<Out>>, Out> {
    override fun bind(executor: IQueryExecutor<In>): IFluxQuery<Out>
    fun <T> map(body: (IMonoStep<Out>) -> IMonoStep<T>): IFluxUnboundQuery<In, T>
    fun <T> flatMap(body: (IMonoStep<Out>) -> IFluxStep<T>): IFluxUnboundQuery<In, T>
}

class MonoUnboundQuery<In, ElementOut>(
    inputStep: QueryInput<In>,
    outputStep: IMonoStep<ElementOut>,
    reference: QueryReference<*>,
    sharedSteps: List<SharedStep<*>>,
) : UnboundQuery<In, ElementOut, ElementOut>(
    inputStep,
    outputStep,
    reference as QueryReference<UnboundQuery<In, ElementOut, ElementOut>>,
    sharedSteps,
),
    IMonoUnboundQuery<In, ElementOut> {

    override val outputStep: IMonoStep<ElementOut>
        get() = super.outputStep as IMonoStep<ElementOut>

    override fun bind(executor: IQueryExecutor<In>): IMonoQuery<ElementOut> = MonoBoundQuery(executor, this)

    override suspend fun execute(evaluationContext: QueryEvaluationContext, input: IStepOutput<In>): IStepOutput<ElementOut> {
        try {
            return asFlow(evaluationContext, input).single()
        } catch (ex: NoSuchElementException) {
            throw RuntimeException("Empty query result: " + this, ex)
        }
    }

    override fun <T> map(query: IMonoUnboundQuery<ElementOut, T>): IMonoUnboundQuery<In, T> {
        return buildMonoQuery<In, T> { it.map(this@MonoUnboundQuery).map(query) }
    }

    override fun <T> map(query: IFluxUnboundQuery<ElementOut, T>): IFluxUnboundQuery<In, T> {
        return buildFluxQuery { it.map(this@MonoUnboundQuery).flatMap(query) }
    }

    override fun getAggregationOutputSerializer(serializationContext: SerializationContext): KSerializer<IStepOutput<ElementOut>> {
        return outputStep.getOutputSerializer(serializationContext).upcast()
    }

    override fun getElementOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<ElementOut>> {
        return outputStep.getOutputSerializer(serializationContext).upcast()
    }
}

class FluxUnboundQuery<In, ElementOut>(
    inputStep: QueryInput<In>,
    outputStep: IFluxStep<ElementOut>,
    reference: QueryReference<*>,
    sharedSteps: List<SharedStep<*>>,
) : UnboundQuery<In, List<IStepOutput<ElementOut>>, ElementOut>(
    inputStep,
    outputStep,
    reference as QueryReference<UnboundQuery<In, List<IStepOutput<ElementOut>>, ElementOut>>,
    sharedSteps,
),
    IFluxUnboundQuery<In, ElementOut> {

    override val outputStep: IFluxStep<ElementOut>
        get() = super.outputStep as IFluxStep<ElementOut>

    override fun bind(executor: IQueryExecutor<In>): IFluxQuery<ElementOut> = FluxBoundQuery(executor, this)

    override suspend fun execute(evaluationContext: QueryEvaluationContext, input: IStepOutput<In>): IStepOutput<List<IStepOutput<ElementOut>>> {
        return asFlow(evaluationContext, input).toList().asStepOutput(null)
    }

    override fun <T> map(body: (IMonoStep<ElementOut>) -> IMonoStep<T>): IFluxUnboundQuery<In, T> {
        return buildFluxQuery { it.flatMap(this@FluxUnboundQuery).map { body(it) } }
    }

    override fun <T> flatMap(body: (IMonoStep<ElementOut>) -> IFluxStep<T>): IFluxUnboundQuery<In, T> {
        return buildFluxQuery { it.flatMap(this@FluxUnboundQuery).flatMap(body) }
    }

    override fun getAggregationOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<List<IStepOutput<ElementOut>>>> {
        return ListSerializer(outputStep.getOutputSerializer(serializationContext).upcast()).stepOutputSerializer(null)
    }

    override fun getElementOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<ElementOut>> {
        return outputStep.getOutputSerializer(serializationContext).upcast()
    }
}

fun <In, Out> IMonoUnboundQuery<In, Out>.castToInstance(): MonoUnboundQuery<In, Out> = this as MonoUnboundQuery<In, Out>
fun <In, Out> IFluxUnboundQuery<In, Out>.castToInstance(): FluxUnboundQuery<In, Out> = this as FluxUnboundQuery<In, Out>
fun <In, Out, ElementOut> IUnboundQuery<In, Out, ElementOut>.castToInstance(): UnboundQuery<In, Out, ElementOut> = this as UnboundQuery<In, Out, ElementOut>

abstract class UnboundQuery<In, AggregationOut, ElementOut>(
    val inputStep: QueryInput<In>,
    private val outputStep_: IProducingStep<ElementOut>,
    override val reference: QueryReference<UnboundQuery<In, AggregationOut, ElementOut>>,
    val sharedSteps: List<SharedStep<*>>,
) : IUnboundQuery<In, AggregationOut, ElementOut> {

    init {
        if (reference.queryId == null) reference.queryId = generateId()
        reference.providedQuery = this
        require(inputStep.owner == reference) { "Input $inputStep isn't owned by query ${reference.queryId}" }
    }

    open val outputStep: IProducingStep<ElementOut> get() = outputStep_

    private lateinit var unconsumedSideEffectSteps: List<IProducingStep<*>>
    private var anyStepNeedsCoroutineScope = true
    private var requiresSingularInput = true
    private var isSinglePath = false
    private var canOptimizeFlows = false
    private var validated = false
    fun validate() {
        validated = true
        for (step in getOwnSteps()) {
            step.validate()
        }

        val mostSpecificSteps = getOwnSteps().toMutableSet()
        mostSpecificSteps.toList().forEach {
            val input = (it as? IConsumingStep<*>)?.getProducers()?.singleOrNull()
            if (input?.getRootInputSteps() == it.getRootInputSteps()) {
                mostSpecificSteps.remove(input)
            }
        }
        val illegalCrossQueryReferences = mostSpecificSteps.map {
            val foreignInputs = getForeignInputs(it)
            if (foreignInputs.isNotEmpty()) {
                "Query ${reference.queryId}: Step uses inputs ${it.getRootInputSteps()} from multiple queries. Use .shared(): $it"
            } else {
                null
            }
        }.filterNotNull().toList()
        if (illegalCrossQueryReferences.isNotEmpty()) {
            throw CrossQueryReferenceException(
                illegalCrossQueryReferences
                    .sortedByDescending { it.length }
                    .joinToString("\n"),
            )
        }
        require(outputStep !is SharedStep<*>) { "Not allowed as output step: $outputStep" }

        unconsumedSideEffectSteps = (getOwnSteps() - outputStep)
            .filterIsInstance<IProducingStep<*>>()
            .filter { it.hasSideEffect() }
            .filter { !isConsumed(it) }
        anyStepNeedsCoroutineScope = sharedSteps.isNotEmpty() || getOwnSteps().any { it.needsCoroutineScope() }
        requiresSingularInput = getOwnSteps().any { it.requiresSingularQueryInput() } // inputStep.requiresSingularQueryInput()
        isSinglePath = (getOwnSteps() - setOf(outputStep)).all { it is IProducingStep<*> && it.getConsumers().size == 1 } &&
            (getOwnSteps() - setOf(inputStep)).all { it is IConsumingStep<*> && it.getProducers().size == 1 }

        canOptimizeFlows = isSinglePath && !requiresSingularInput && unconsumedSideEffectSteps.isEmpty() && !anyStepNeedsCoroutineScope && sharedSteps.isEmpty()
    }

    private fun getForeignInputs(it: IStep) = it.getRootInputSteps().filterIsInstance<QueryInput<*>>().toSet().minus(inputStep)

    override fun requiresWriteAccess(): Boolean {
        return getOwnSteps().any { it.requiresWriteAccess() }
    }

    override fun canBeEmpty(): Boolean {
        return outputStep.canBeEmpty()
    }

    override fun toString(): String {
        try {
            return "${reference.queryId}#" + (getUnconsumedSteps() + outputStep).joinToString("; ")
        } catch (ex: Throwable) {
            return "Query#${reference.queryId}"
        }
    }

    private fun isConsumed(step: IStep): Boolean {
        return when (step) {
            outputStep -> true
            is IProducingStep<*> -> step.getConsumers().any { isConsumed(it) }
            else -> false
        }
    }

    fun getOwnSteps() = getAllSteps().filter { it.owner == reference }

    fun getAllSteps(): Set<IStep> {
        val allSteps = HashSet<IStep>()
        inputStep.collectAllSteps(allSteps)
        outputStep.collectAllSteps(allSteps)
        return allSteps
    }

    override fun asFlow(evaluationContext: QueryEvaluationContext, input: StepFlow<In>): StepFlow<ElementOut> {
        check(validated) { "call validate() first" }
        if (canOptimizeFlows) {
            return SinglePathFlowInstantiationContext(evaluationContext, inputStep, input).getOrCreateFlow(outputStep)
            // return input.flatMapConcat { SinglePathFlowInstantiationContext(inputStep, flowOf(it)).getOrCreateFlow(outputStep) }
        } else {
            return flow<IStepOutput<ElementOut>> {
                input.collect { inputElement ->
                    suspend fun body(context: FlowInstantiationContext) {
                        context.put(inputStep, flowOf(inputElement))

                        for (sharedStep in sharedSteps) {
                            val values: List<IStepOutput<Any?>> = context.getOrCreateFlow(sharedStep.getProducer()).toList()
                            context.put(sharedStep, values.asFlow())
                            context.evaluationContext = context.evaluationContext + (sharedStep to values)
                        }

                        val outputFlow = context.getOrCreateFlow(outputStep)

                        // ensure all write operations are executed
                        unconsumedSideEffectSteps
                            .mapNotNull {
                                if (context.getFlow(it) == null) context.getOrCreateFlow(it) else null
                            }
                            .forEach { it.collect() }

                        outputFlow.collect { outputElement ->
                            emit(outputElement)
                        }
                    }
                    if (anyStepNeedsCoroutineScope) {
                        coroutineScope {
                            body(FlowInstantiationContext(evaluationContext, this, this@UnboundQuery))
                        }
                    } else {
                        body(FlowInstantiationContext(evaluationContext, null, this@UnboundQuery))
                    }
                }
            }
        }
    }

    private fun getUnconsumedSteps(): List<IProducingStep<*>> {
        return (getOwnSteps() - outputStep)
            .filterIsInstance<IProducingStep<*>>()
            .filter { it.getConsumers().isEmpty() }
    }

    fun createDescriptor(): QueryGraphDescriptor {
        val builder = QueryGraphDescriptorBuilder()
        builder.load(this)
        return builder.build()
    }

    companion object {
        private val idSequence: AtomicLong = AtomicLong(1000)
        fun generateId(): Long = idSequence.incrementAndGet()

        val serializersModule = SerializersModule {
            polymorphic(StepDescriptor::class) {
                subclass(org.modelix.modelql.core.AllowEmptyStep.Descriptor::class)
                subclass(org.modelix.modelql.core.AndOperatorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.AssertNotEmptyStep.Descriptor::class)
                subclass(org.modelix.modelql.core.CollectionSizeStep.Descriptor::class)
                subclass(org.modelix.modelql.core.ConstantSourceStep.Descriptor::class, ConstantSourceStep.Descriptor.Serializer())
                subclass(org.modelix.modelql.core.CountingStep.CountDescriptor::class)
                subclass(org.modelix.modelql.core.DropStep.Descriptor::class)
                subclass(org.modelix.modelql.core.EmptyStringIfNullStep.Descriptor::class)
                subclass(org.modelix.modelql.core.EqualsOperatorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.FilteringStep.Descriptor::class)
                subclass(org.modelix.modelql.core.FirstElementStep.FirstElementDescriptor::class)
                subclass(org.modelix.modelql.core.FirstOrNullStep.Descriptor::class)
                subclass(org.modelix.modelql.core.FlatMapStep.Descriptor::class)
                subclass(org.modelix.modelql.core.IdentityStep.IdentityStepDescriptor::class)
                subclass(org.modelix.modelql.core.IfEmptyStep.Descriptor::class)
                subclass(org.modelix.modelql.core.InPredicate.Descriptor::class)
                subclass(org.modelix.modelql.core.IntSumStep.IntSumDescriptor::class)
                subclass(org.modelix.modelql.core.IsEmptyStep.Descriptor::class)
                subclass(org.modelix.modelql.core.IsNullPredicateStep.Descriptor::class)
                subclass(org.modelix.modelql.core.JoinStep.Descriptor::class)
                subclass(org.modelix.modelql.core.ListCollectorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.MapAccessStep.Descriptor::class)
                subclass(org.modelix.modelql.core.MapCollectorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.MapIfNotNullStep.Descriptor::class)
                subclass(org.modelix.modelql.core.MappingStep.Descriptor::class)
                subclass(org.modelix.modelql.core.NotOperatorStep.NotDescriptor::class)
                subclass(org.modelix.modelql.core.NullIfEmpty.OrNullDescriptor::class)
                subclass(org.modelix.modelql.core.OrOperatorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.PrintStep.Descriptor::class)
                subclass(org.modelix.modelql.core.QueryCallStep.Descriptor::class)
                subclass(org.modelix.modelql.core.QueryInput.Descriptor::class)
                subclass(org.modelix.modelql.core.RegexPredicate.Descriptor::class)
                subclass(org.modelix.modelql.core.SetCollectorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.SharedStep.Descriptor::class)
                subclass(org.modelix.modelql.core.SingleStep.Descriptor::class)
                subclass(org.modelix.modelql.core.StringContainsPredicate.StringContainsDescriptor::class)
                subclass(org.modelix.modelql.core.StringToBooleanStep.Descriptor::class)
                subclass(org.modelix.modelql.core.StringToIntStep.Descriptor::class)
                subclass(org.modelix.modelql.core.TakeStep.Descriptor::class)
                subclass(org.modelix.modelql.core.ToStringStep.Descriptor::class)
                subclass(org.modelix.modelql.core.WhenStep.Descriptor::class)
                subclass(org.modelix.modelql.core.WithIndexStep.Descriptor::class)
                subclass(org.modelix.modelql.core.ZipElementAccessStep.Descriptor::class)
                subclass(org.modelix.modelql.core.ZipStep.Descriptor::class)
            }
        }
    }
}

class SinglePathFlowInstantiationContext(
    override val evaluationContext: QueryEvaluationContext,
    val queryInput: QueryInput<*>,
    val inputFlow: StepFlow<*>,
) : IFlowInstantiationContext {
    override val coroutineScope: CoroutineScope?
        get() = null

    override fun <T> getOrCreateFlow(step: IProducingStep<T>): StepFlow<T> {
        return if (step == queryInput) inputFlow as StepFlow<T> else step.createFlow(this)
    }

    override fun <T> getFlow(step: IProducingStep<T>): Flow<T>? {
        return null
    }
}

private fun IStep.collectAllSteps(result: MutableSet<IStep> = LinkedHashSet<IStep>()): Set<IStep> {
    if (!result.contains(this)) {
        result.add(this)
        getDirectlyConnectedSteps().forEach { it.collectAllSteps(result) }
    }
    return result
}

private fun IStep.getDirectlyConnectedSteps(): Set<IStep> {
    return (
        (if (this is IConsumingStep<*>) getProducers() else emptyList()) +
            (if (this is IProducingStep<*>) getConsumers() else emptyList())
        ).toSet()
}

class QueryInput<E> : ProducingStep<E>(), IMonoStep<E> {

    override fun toString(): String = "it<${owner.queryId}>"

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return (
            serializationContext.queryInputSerializers[this]
                ?: (NothingSerializer() as KSerializer<E>).stepOutputSerializer(this)
            ) as KSerializer<out IStepOutput<E>>
    }

    override fun createFlow(context: IFlowInstantiationContext): StepFlow<E> {
        throw RuntimeException("The flow for the query input step is expected to be created by the query")
    }

    override fun canBeEmpty(): Boolean = false
    override fun canBeMultiple(): Boolean = false

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("input")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return QueryInput<Any?>()
        }
    }
}

fun <T> KSerializer<out T>.upcast(): KSerializer<T> = this as KSerializer<T>

class CrossQueryReferenceException(message: String) : Exception(message)
