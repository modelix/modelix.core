package org.modelix.modelql.core

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

interface IQuery<In, Out> {
    suspend fun run(input: In): Out
}

class Query<In, Out>(val inputStep: QueryInput<In>, val outputStep: IProducingStep<Out>) : IQuery<In, Out> {

    init {
        for (step in getAllSteps()) {
            if (step.owningQuery != null) throw IllegalStateException("$step is already part of ${step.owningQuery}")
            step.owningQuery = this
        }
        validate()
    }

    override fun toString(): String {
        return "$outputStep"
    }

    fun validate() {
        for (step in getAllSteps()) {
            step.validate()
        }
    }

    fun optimize(): Query<In, Out> {
        return this
    }

    fun validateAndOptimize(): Query<In, Out> {
        validate()
        return optimize()
    }

    fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Out> = outputStep.getOutputSerializer(serializersModule) as KSerializer<Out>

    fun getAllSteps(): Set<IStep> {
        val allSteps = HashSet<IStep>()
        inputStep.collectAllSteps(allSteps)
        outputStep.collectAllSteps(allSteps)
        return allSteps
    }

    override suspend fun run(input: In): Out {
        try {
            return applyQuery(input).single()
        } catch (ex: NoSuchElementException) {
            throw RuntimeException("Empty query result: " + this@Query, ex)
        }
    }

    suspend fun runList(input: In): List<Out> {
        return applyQuery(input).toList()
    }

    fun applyQuery(inputElement: In): Flow<Out> {
        return applyQuery(flowOf(inputElement))
    }

    @OptIn(FlowPreview::class)
    fun applyQuery(input: Flow<In>): Flow<Out> {
        return channelFlow {
            coroutineScope {
                input.flatMapMerge {
                    val context = FlowInstantiationContext(this)
                    context.put(inputStep, flowOf(it))
                    context.getOrCreateFlow(outputStep)
                }.collect {
                    send(it)
                }
            }
        }
    }

    fun createDescriptor(): QueryDescriptor {
        val builder = QueryDescriptorBuilder()
        builder.load(getAllSteps().asSequence())
        return QueryDescriptor(builder.stepDescriptors.values.toList(), builder.connections.toList(), builder.stepId(inputStep), builder.stepId(outputStep))
    }

    companion object {
        val serializersModule = SerializersModule {
            polymorphic(StepDescriptor::class) {
                subclass(org.modelix.modelql.core.AndOperatorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.BooleanSourceStep.Descriptor::class)
                subclass(org.modelix.modelql.core.CountingStep.CountDescriptor::class)
                subclass(org.modelix.modelql.core.EmptyStringIfNullStep.Descriptor::class)
                subclass(org.modelix.modelql.core.FirstElementStep.FirstElementDescriptor::class)
                subclass(org.modelix.modelql.core.FirstOrNullStep.Descriptor::class)
                subclass(org.modelix.modelql.core.FlatMapStep.Descriptor::class)
                subclass(org.modelix.modelql.core.FluxFilteringStep.Descriptor::class)
                subclass(org.modelix.modelql.core.FluxMappingStep.Descriptor::class)
                subclass(org.modelix.modelql.core.IdentityStep.IdentityStepDescriptor::class)
                subclass(org.modelix.modelql.core.IfEmptyStep.Descriptor::class)
                subclass(org.modelix.modelql.core.InPredicate.Descriptor::class)
                subclass(org.modelix.modelql.core.IntEqualsOperatorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.IntSumStep.IntSumDescriptor::class)
                subclass(org.modelix.modelql.core.IsNullPredicateStep.Descriptor::class)
                subclass(org.modelix.modelql.core.JoinStep.Descriptor::class)
                subclass(org.modelix.modelql.core.ListCollectorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.MonoFilteringStep.Descriptor::class)
                subclass(org.modelix.modelql.core.MonoMappingStep.Descriptor::class)
                subclass(org.modelix.modelql.core.NotOperatorStep.NotDescriptor::class)
                subclass(org.modelix.modelql.core.NullIfEmpty.OrNullDescriptor::class)
                subclass(org.modelix.modelql.core.OrOperatorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.QueryInput.Descriptor::class)
                subclass(org.modelix.modelql.core.RegexPredicate.Descriptor::class)
                subclass(org.modelix.modelql.core.SetCollectorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.StringContainsPredicate.StringContainsDescriptor::class)
                subclass(org.modelix.modelql.core.StringEqualsOperatorStep.Descriptor::class)
                subclass(org.modelix.modelql.core.StringSourceStep.Descriptor::class)
                subclass(org.modelix.modelql.core.StringToBooleanStep.Descriptor::class)
                subclass(org.modelix.modelql.core.StringToIntStep.Descriptor::class)
                subclass(org.modelix.modelql.core.ZipElementAccessStep.Descriptor::class)
                subclass(org.modelix.modelql.core.ZipStep.Descriptor::class)
            }
        }
        fun <RemoteIn, RemoteOut> build(body: (IMonoStep<RemoteIn>) -> IProducingStep<RemoteOut>): Query<RemoteIn, RemoteOut> {
            val inputStep = QueryInput<RemoteIn>()
            val outputStep = body(inputStep)
            return Query(inputStep, outputStep)
        }
    }
}

private class QueryDescriptorBuilder {
    val stepDescriptors = LinkedHashMap<IStep, StepDescriptor>()
    val connections = LinkedHashSet<PortConnection>()

    fun createStep(step: IStep): StepDescriptor {
        val newDescriptor = step.createDescriptor()
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
            consumer.getProducers().forEach { producer ->
                connections += PortConnection(
                    PortReference(producer.id(), consumer.getProducers().indexOf(producer)),
                    PortReference(consumer.id(), producer.getConsumers().indexOf(consumer))
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
    internal var indirectConsumer: IConsumingStep<E>? = null
    override fun toString(): String = "it"

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out E> {
        val c = indirectConsumer ?: throw UnsupportedOperationException()
        return c.getProducers().single().getOutputSerializer(serializersModule) as KSerializer<out E>
    }

    override fun createFlow(context: IFlowInstantiationContext): Flow<E> {
        throw RuntimeException("The flow for the query input step is expected to be created by the query")
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("input")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return QueryInput<Any?>()
        }
    }
}
