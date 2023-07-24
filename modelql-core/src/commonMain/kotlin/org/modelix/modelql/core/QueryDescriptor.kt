package org.modelix.modelql.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

typealias QueryId = Long

class QueryGraphDescriptorBuilder {
    private val queries: MutableMap<UnboundQuery<*, *, *>, QueryDescriptorBuilder> = LinkedHashMap()
    val stepDescriptors = LinkedHashMap<IStep, StepDescriptor>()
    val connections = LinkedHashSet<PortConnection>()

    fun build(): QueryGraphDescriptor {
        createConnections()
        return QueryGraphDescriptor(
            queries.values.map { it.buildQueryDescriptor() },
            stepDescriptors.values.toList(),
            connections.toList()
        )
    }

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

    fun <Q : IUnboundQuery<*, *, *>> load(query: Q): QueryId {
        require(query is UnboundQuery<*, *, *>)
        if (!queries.containsKey(query)) {
            val queryBuilder = QueryDescriptorBuilder(query)
            queries[query] = queryBuilder
            load(query.getAllSteps().asSequence())
        }
        return query.reference.getId()
    }

    fun load(steps: Sequence<IStep>) {
        for (step in steps) {
            if (!stepDescriptors.containsKey(step)) {
                createStep(step)
            }
        }
    }

    fun createStep(step: IStep): StepDescriptor {
        val newDescriptor = step.createDescriptor(this)
        newDescriptor.id = stepDescriptors.size
        stepDescriptors[step] = newDescriptor
        return newDescriptor
    }

    fun IStep.id(): Int = stepId(this)
    fun stepId(step: IStep): Int = stepDescriptors[step]!!.id!!

    inner class QueryDescriptorBuilder(val query: UnboundQuery<*, *, *>) {
        fun getGraphBuilder(): QueryGraphDescriptorBuilder = this@QueryGraphDescriptorBuilder
        fun buildQueryDescriptor(): QueryDescriptor {
            return QueryDescriptor(
                stepId(query.inputStep),
                stepId(query.outputStep),
                query is FluxUnboundQuery,
                query.reference.getId()
            )
        }
    }
}

@Serializable
data class QueryGraphDescriptor(
    val queries: List<QueryDescriptor>,
    val steps: List<StepDescriptor>,
    val connections: List<PortConnection>
) {
    fun initStepIds() {
        steps.forEachIndexed { index, stepDescriptor -> stepDescriptor.id = index }
    }

    fun createRootQuery(): UnboundQuery<*, *, *> {
        return QueryDeserializationContext(this).createQueries().first()
    }
}

@Serializable
data class QueryDescriptor(
    val input: Int,
    val output: Int,
    val isFluxOutput: Boolean,
    val queryId: Long
)

@Serializable
abstract class StepDescriptor {
    @Transient
    var id: Int? = null
    abstract fun createStep(context: QueryDeserializationContext): IStep
}

sealed class CoreStepDescriptor : StepDescriptor()

@Serializable
data class PortConnection(val producer: PortReference, val consumer: PortReference)

@Serializable
data class PortReference(val step: Int, val port: Int = 0)

sealed interface IQueryReference<out Q : IUnboundQuery<*, *, *>> {
    val query: Q
    fun getId(): Long
}
class QueryReference<Q : IUnboundQuery<*, *, *>>(
    var providedQuery: Q?,
    var queryId: Long?,
    private val queryInitializer: (() -> Q)?
) : IQueryReference<Q> {
    private val creatingStacktrace = Exception()
    override val query: Q by lazy {
        providedQuery ?: (queryInitializer ?: throw IllegalStateException("query for ID $queryId not found", creatingStacktrace)).invoke()
    }
    override fun getId(): Long = queryId ?: query.reference.takeIf { it != this }?.getId() ?: throw RuntimeException("ID not set")
}

class QueryDeserializationContext(val graphDescriptor: QueryGraphDescriptor) {
    init {
        graphDescriptor.initStepIds()
    }
    private val id2stepDesc = graphDescriptor.steps.associateBy { requireNotNull(it.id) { "Step has no ID: $it" } }
    private val id2queryDesc = graphDescriptor.queries.associateBy { it.queryId }
    private val createdSteps = HashMap<Int, IStep>()
    private val createdQueryReferences = HashMap<QueryId, QueryReference<*>>()
    private val createdQueries = HashMap<QueryId, UnboundQuery<*, *, *>>()

    fun createQueries(): List<UnboundQuery<*, *, *>> {
        createConnections()
        return graphDescriptor.queries.map { getOrCreateQuery(it.queryId) }
    }

    fun createConnections() {
        for (connection in graphDescriptor.connections.sortedBy { it.consumer.port }) {
            val producer = getOrCreateStep(connection.producer.step) as IProducingStep<Any?>
            val consumer = getOrCreateStep(connection.consumer.step) as IConsumingStep<Any?>
            consumer.connect(producer)
        }

        // validate connection order
        for (connection in graphDescriptor.connections) {
            val producer = getOrCreateStep(connection.producer.step) as IProducingStep<Any?>
            val consumer = getOrCreateStep(connection.consumer.step) as IConsumingStep<Any?>
            val expectedPortAtConsumer = connection.consumer.port
            val actualPortAtConsumer = consumer.getProducers().indexOf(producer)
            if (expectedPortAtConsumer != actualPortAtConsumer) {
                throw RuntimeException("wrong connection order: $consumer")
            }
        }
    }

    fun getOrCreateStep(id: Int): IStep {
        return createdSteps.getOrPut(id) {
            val desc = id2stepDesc[id]
            requireNotNull(desc) { "Step $id not found" }
            desc.createStep(this)
        }
    }

    fun getOrCreateQueryReference(id: QueryId): QueryReference<*> {
        return createdQueryReferences.getOrPut(id) {
            QueryReference(null, id, null)
        }
    }

    fun getOrCreateQuery(id: QueryId): UnboundQuery<*, *, *> {
        return createdQueries.getOrPut(id) {
            val desc = id2queryDesc[id]
            requireNotNull(desc) { "Query $id not found" }
            val inputStep = getOrCreateStep(desc.input) as QueryInput<Any?>
            val outputStep = getOrCreateStep(desc.output) as IProducingStep<Any?>
            val reference = getOrCreateQueryReference(id)

            // Some steps implement IMonoStep and IFluxStep. An instanceOf check doesn't work.
            return if (desc.isFluxOutput) {
                FluxUnboundQuery<Any?, Any?>(inputStep, outputStep as IFluxStep<Any?>, reference as QueryReference<IFluxUnboundQuery<Any?, Any?>>)
            } else {
                MonoUnboundQuery<Any?, Any?>(inputStep, outputStep as IMonoStep<Any?>, reference as QueryReference<UnboundQuery<Any?, Any?, Any?>>)
            }
        }
    }
}
