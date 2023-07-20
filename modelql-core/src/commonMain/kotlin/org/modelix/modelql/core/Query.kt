package org.modelix.modelql.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

interface IQueryExecutor<out In> {
    fun <Out> createFlow(query: IUnboundQuery<In, *, Out>): StepFlow<Out>
}

class SimpleQueryExecutor<E>(val input: E) : IQueryExecutor<E> {
    override fun <ElementOut> createFlow(query: IUnboundQuery<E, *, ElementOut>): StepFlow<ElementOut> {
        return query.asFlow(input)
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
        return executor.createFlow(query).toList().asStepOutput()
    }

    override fun <T> flatMap(body: (IMonoStep<Out>) -> IFluxStep<T>): IFluxQuery<T> {
        return query.flatMap(body).bind(executor)
    }

    override fun <T> map(body: (IMonoStep<Out>) -> IMonoStep<T>): IFluxQuery<T> {
        return query.map(body).bind(executor)
    }
}

interface IUnboundQuery<in In, out AggregationOut, out ElementOut> {
    val id: Long
    suspend fun execute(input: In): IStepOutput<AggregationOut>
    fun asFlow(input: StepFlow<In>): StepFlow<ElementOut>
    fun asFlow(input: In): StepFlow<ElementOut> = asFlow(flowOf(input).asStepFlow())
    fun asFlow(input: IStepOutput<In>): StepFlow<ElementOut> = asFlow(flowOf(input))
    fun asSequence(input: Sequence<In>): Sequence<ElementOut>

    fun requiresWriteAccess(): Boolean
    fun canBeEmpty(): Boolean

    fun bind(executor: IQueryExecutor<In>): IQuery<AggregationOut, ElementOut>
    fun getElementOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<ElementOut>>
    fun getAggregationOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<AggregationOut>>

    companion object {
        fun <In, Out> build(body: (IMonoStep<In>) -> IMonoStep<Out>): IMonoUnboundQuery<In, Out> {
            return buildMono(body)
        }
        fun <In, Out> buildMono(body: (IMonoStep<In>) -> IMonoStep<Out>): IMonoUnboundQuery<In, Out> {
            val inputStep = QueryInput<In>()
            val outputStep = body(inputStep)
            return MonoUnboundQuery(inputStep, outputStep, id = null)
        }
        fun <In, Out> buildFlux(body: (IMonoStep<In>) -> IFluxStep<Out>): IFluxUnboundQuery<In, Out> {
            val inputStep = QueryInput<In>()
            val outputStep = body(inputStep)
            return FluxUnboundQuery(inputStep, outputStep, id = null)
        }
    }
}

interface IMonoUnboundQuery<in In, out Out> : IUnboundQuery<In, Out, Out> {
    override fun bind(executor: IQueryExecutor<In>): IMonoQuery<Out>
    fun <T> map(query: IMonoUnboundQuery<Out, T>): IMonoUnboundQuery<In, T>
    fun <T> map(query: IFluxUnboundQuery<Out, T>): IFluxUnboundQuery<In, T>
    fun <T> map(body: (IMonoStep<Out>) -> IMonoStep<T>): IMonoUnboundQuery<In, T> = map(IUnboundQuery.buildMono(body))
    fun <T> flatMap(body: (IMonoStep<Out>) -> IFluxStep<T>): IFluxUnboundQuery<In, T> = map(IUnboundQuery.buildFlux(body))
    fun evaluate(input: In): Optional<Out>
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
    id: Long?
) : UnboundQuery<In, ElementOut, ElementOut>(inputStep, outputStep, id), IMonoUnboundQuery<In, ElementOut> {

    override val outputStep: IMonoStep<ElementOut>
        get() = super.outputStep as IMonoStep<ElementOut>

    override fun bind(executor: IQueryExecutor<In>): IMonoQuery<ElementOut> = MonoBoundQuery(executor, this)

    override suspend fun execute(input: In): IStepOutput<ElementOut> {
        try {
            return asFlow(input).single()
        } catch (ex: NoSuchElementException) {
            throw RuntimeException("Empty query result: " + this, ex)
        }
    }

    override fun <T> map(query: IMonoUnboundQuery<ElementOut, T>): IMonoUnboundQuery<In, T> {
        return IUnboundQuery.buildMono { it.map(this).map(query) }
    }

    override fun <T> map(query: IFluxUnboundQuery<ElementOut, T>): IFluxUnboundQuery<In, T> {
        return IUnboundQuery.buildFlux { it.map(this).flatMap(query) }
    }

    override fun getAggregationOutputSerializer(serializersModule: SerializersModule): KSerializer<IStepOutput<ElementOut>> {
        return outputStep.getOutputSerializer(serializersModule).upcast()
    }

    override fun getElementOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<ElementOut>> {
        return outputStep.getOutputSerializer(serializersModule).upcast()
    }

    override fun evaluate(input: In): Optional<ElementOut> {
        return outputStep.evaluate(input)
    }
}

class FluxUnboundQuery<In, ElementOut>(
    inputStep: QueryInput<In>,
    outputStep: IFluxStep<ElementOut>,
    id: Long?
) : UnboundQuery<In, List<IStepOutput<ElementOut>>, ElementOut>(inputStep, outputStep, id), IFluxUnboundQuery<In, ElementOut> {

    override val outputStep: IFluxStep<ElementOut>
        get() = super.outputStep as IFluxStep<ElementOut>

    override fun bind(executor: IQueryExecutor<In>): IFluxQuery<ElementOut> = FluxBoundQuery(executor, this)

    override suspend fun execute(input: In): IStepOutput<List<IStepOutput<ElementOut>>> {
        return asFlow(input).toList().asStepOutput()
    }

    override fun <T> map(body: (IMonoStep<ElementOut>) -> IMonoStep<T>): IFluxUnboundQuery<In, T> {
        return IUnboundQuery.buildFlux { it.flatMap(this).map(body) }
    }

    override fun <T> flatMap(body: (IMonoStep<ElementOut>) -> IFluxStep<T>): IFluxUnboundQuery<In, T> {
        return IUnboundQuery.buildFlux { it.flatMap(this).flatMap(body) }
    }

    override fun getAggregationOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<List<IStepOutput<ElementOut>>>> {
        return ListSerializer(outputStep.getOutputSerializer(serializersModule).upcast()).stepOutputSerializer()
    }

    override fun getElementOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<ElementOut>> {
        return outputStep.getOutputSerializer(serializersModule).upcast()
    }
}

fun <In, Out> IMonoUnboundQuery<In, Out>.castToInstance(): MonoUnboundQuery<In, Out> = this as MonoUnboundQuery<In, Out>
fun <In, Out> IFluxUnboundQuery<In, Out>.castToInstance(): FluxUnboundQuery<In, Out> = this as FluxUnboundQuery<In, Out>
fun <In, Out, ElementOut> IUnboundQuery<In, Out, ElementOut>.castToInstance(): UnboundQuery<In, Out, ElementOut> = this as UnboundQuery<In, Out, ElementOut>

abstract class UnboundQuery<In, AggregationOut, ElementOut>(
    val inputStep: QueryInput<In>,
    private val outputStep_: IProducingStep<ElementOut>,
    id: Long?
) : IUnboundQuery<In, AggregationOut, ElementOut> {

    override val id: Long = id ?: generateId()

    init {
        validate()
    }

    open val outputStep: IProducingStep<ElementOut> get() = outputStep_

    private val unconsumedSideEffectSteps = (getAllSteps() - outputStep)
        .filterIsInstance<IProducingStep<*>>()
        .filter { it.hasSideEffect() }
        .filter { !isConsumed(it) }
    private val anyStepNeedsCoroutineScope = getAllSteps().any { it.needsCoroutineScope() }
    private val requiresSingularInput = getAllSteps().any { it.requiresSingularQueryInput() } // inputStep.requiresSingularQueryInput()
    private val isSinglePath: Boolean = (getAllSteps() - setOf(outputStep)).all { it is IProducingStep<*> && it.getConsumers().size == 1 } &&
        (getAllSteps() - setOf(inputStep)).all { it is IConsumingStep<*> && it.getProducers().size == 1 }
    private val canOptimizeFlows = isSinglePath && !requiresSingularInput && unconsumedSideEffectSteps.isEmpty() && !anyStepNeedsCoroutineScope

    override fun requiresWriteAccess(): Boolean {
        return getAllSteps().any { it.requiresWriteAccess() }
    }

    override fun canBeEmpty(): Boolean {
        return outputStep.canBeEmpty()
    }

    override fun toString(): String {
        return (getUnconsumedSteps() + outputStep).joinToString("; ")
    }

    fun validate() {
        for (step in getAllSteps()) {
            if (step.owningQuery != null) throw IllegalStateException("$step is already part of ${step.owningQuery}")
            step.owningQuery = this
        }
        for (step in getAllSteps()) {
            step.validate()
        }
    }

    private fun isConsumed(step: IStep): Boolean {
        return when (step) {
            outputStep -> true
            is IProducingStep<*> -> step.getConsumers().any { isConsumed(it) }
            else -> false
        }
    }

    fun getAllSteps(): Set<IStep> {
        val allSteps = HashSet<IStep>()
        inputStep.collectAllSteps(allSteps)
        outputStep.collectAllSteps(allSteps)
        return allSteps
    }

    override fun asFlow(input: StepFlow<In>): StepFlow<ElementOut> {
        if (canOptimizeFlows) {
            return SinglePathFlowInstantiationContext(inputStep, input).getOrCreateFlow(outputStep)
            // return input.flatMapConcat { SinglePathFlowInstantiationContext(inputStep, flowOf(it)).getOrCreateFlow(outputStep) }
        } else {
            return flow<IStepOutput<ElementOut>> {
                input.collect { inputElement ->
                    suspend fun body(context: FlowInstantiationContext) {
                        context.put(inputStep, flowOf(inputElement))

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
                            body(FlowInstantiationContext(this))
                        }
                    } else {
                        body(FlowInstantiationContext(null))
                    }
                }
            }
        }
    }

    override fun asSequence(input: Sequence<In>): Sequence<ElementOut> {
        require(unconsumedSideEffectSteps.isEmpty())
        require(!anyStepNeedsCoroutineScope)
        return if (requiresSingularInput) {
            input.flatMap { outputStep.createSequence(sequenceOf(it)) }
        } else {
            outputStep.createSequence(input)
        }
    }

    private fun getUnconsumedSteps(): List<IProducingStep<*>> {
        return (getAllSteps() - outputStep)
            .filterIsInstance<IProducingStep<*>>()
            .filter { it.getConsumers().isEmpty() }
    }

    fun createDescriptor(): QueryDescriptor = createDescriptor(QuerySerializationContext())
    fun createDescriptor(context: QuerySerializationContext): QueryDescriptor {
        context.registerQuery(this)
        val builder = QueryDescriptorBuilder(context)
        builder.load(getAllSteps().asSequence())
        return QueryDescriptor(
            builder.stepDescriptors.values.toList(),
            builder.connections.toList(),
            builder.stepId(inputStep),
            builder.stepId(outputStep),
            this is FluxUnboundQuery,
            id
        )
    }

    companion object {
        private val idSequence: AtomicLong = AtomicLong(0)
        fun generateId(): Long = idSequence.incrementAndGet()

        val serializersModule = SerializersModule {
            polymorphic(StepDescriptor::class) {
                subclass(org.modelix.modelql.core.AllowEmptyStep.Descriptor::class)
                subclass(org.modelix.modelql.core.AndOperatorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.AssertNotEmptyStep.Descriptor::class)
                subclass(org.modelix.modelql.core.ConstantSourceStep.Descriptor::class, ConstantSourceStep.Descriptor.Serializer())
                subclass(org.modelix.modelql.core.CountingStep.CountDescriptor::class)
                subclass(org.modelix.modelql.core.CollectionSizeStep.Descriptor::class)
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
                subclass(org.modelix.modelql.core.SingleStep.Descriptor::class)
                subclass(org.modelix.modelql.core.StringContainsPredicate.StringContainsDescriptor::class)
                subclass(org.modelix.modelql.core.StringToBooleanStep.Descriptor::class)
                subclass(org.modelix.modelql.core.StringToIntStep.Descriptor::class)
                subclass(org.modelix.modelql.core.ToStringStep.Descriptor::class)
                subclass(org.modelix.modelql.core.WhenStep.Descriptor::class)
                subclass(org.modelix.modelql.core.WithIndexStep.Descriptor::class)
                subclass(org.modelix.modelql.core.ZipElementAccessStep.Descriptor::class)
                subclass(org.modelix.modelql.core.ZipStep.Descriptor::class)
            }
        }
    }
}

class SinglePathFlowInstantiationContext(val queryInput: QueryInput<*>, val inputFlow: StepFlow<*>) : IFlowInstantiationContext {
    override val coroutineScope: CoroutineScope?
        get() = null

    override fun <T> getOrCreateFlow(step: IProducingStep<T>): StepFlow<T> {
        return if (step == queryInput) inputFlow as StepFlow<T> else step.createFlow(this)
    }

    override fun <T> getFlow(step: IProducingStep<T>): Flow<T>? {
        return null
    }
}

private class QueryDescriptorBuilder(val context: QuerySerializationContext) {
    val stepDescriptors = LinkedHashMap<IStep, StepDescriptor>()
    val connections = LinkedHashSet<PortConnection>()

    fun createStep(step: IStep): StepDescriptor {
        val newDescriptor = step.createDescriptor(context)
        newDescriptor.id = stepDescriptors.size
        stepDescriptors[step] = newDescriptor
        return newDescriptor
    }

    fun load(steps: Sequence<IStep>) {
        for (step in steps) {
            createStep(step)
        }
        createConnections()
    }

    fun IStep.id(): Int = stepId(this)
    fun stepId(step: IStep): Int = stepDescriptors[step]!!.id!!

    fun createConnections() {
        // iterating over the consumers is sufficient, because there can't be a connection with only producers
        for (consumer in stepDescriptors.keys.filterIsInstance<IConsumingStep<*>>()) {
            consumer.getProducers().forEachIndexed { producerIndex, producer ->
                connections += PortConnection(
                    PortReference(producer.id(), producer.getConsumers().indexOf(consumer)),
                    PortReference(consumer.id(), producerIndex)
                )
            }
        }
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
    @Transient
    internal var indirectConsumer: IConsumingStep<E>? = null
    override fun toString(): String = "it"

    override fun createSequence(queryInput: Sequence<Any?>): Sequence<E> {
        return queryInput as Sequence<E>
    }

    override fun evaluate(queryInput: Any?): Optional<E> {
        return Optional.of(queryInput as E)
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E>> {
        val c = indirectConsumer ?: throw UnsupportedOperationException()
        return (c.getProducers().single() as IProducingStep<E>).getOutputSerializer(serializersModule)
    }

    fun getQueryInputProducer(): IProducingStep<E>? {
        val c = indirectConsumer ?: return null
        return c.getProducers().single() as IProducingStep<E>
    }

    override fun createFlow(context: IFlowInstantiationContext): StepFlow<E> {
        throw RuntimeException("The flow for the query input step is expected to be created by the query")
    }

    override fun canBeEmpty(): Boolean = false
    override fun canBeMultiple(): Boolean = false

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor()

    @Serializable
    @SerialName("input")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return QueryInput<Any?>()
        }
    }
}

fun <T> KSerializer<out T>.upcast(): KSerializer<T> = this as KSerializer<T>
