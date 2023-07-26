package org.modelix.modelql.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

typealias QueryId = Long

class QueryGraphDescriptorBuilder {
    private val queries: MutableMap<UnboundQuery<*, *, *>, QueryDescriptorBuilder> = LinkedHashMap()
    private val stepDescriptors = LinkedHashMap<IStep, StepDescriptor>()
    private val connections = LinkedHashSet<PortConnection>()
    private var nextStepId: Int = 0

    fun build(): QueryGraphDescriptor {
        createConnections()
        return QueryGraphDescriptor(
            queries.values.map { it.buildQueryDescriptor() },
            stepDescriptors.values.associate { it.id!! to it },
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
            if (!stepDescriptors.isComputing(step)) {
                createStep(step)
            }
        }
    }

    fun createStep(step: IStep): StepDescriptor {
        return stepDescriptors.getOrCompute(step) {
            step.createDescriptor(this).also {
                it.id = nextStepId++
                it.owner = step.owner.queryId!!
            }
        }
    }

    fun IStep.id(): Int = stepId(this)
    fun stepId(step: IStep): Int = requireNotNull(requireNotNull(stepDescriptors[step]) { "No descriptor: $step" }.id) { "No ID: $step" }

    inner class QueryDescriptorBuilder(val query: UnboundQuery<*, *, *>) {
        fun getGraphBuilder(): QueryGraphDescriptorBuilder = this@QueryGraphDescriptorBuilder
        fun buildQueryDescriptor(): QueryDescriptor {
            val outputStepId = stepId(query.outputStep)
            val inputStepId = stepId(query.inputStep)
            return QueryDescriptor(
                inputStepId,
                outputStepId,
                query.sharedSteps.map { stepId(it) },
                query is FluxUnboundQuery,
                query.reference.getId()
            )
        }
    }
}

@Serializable
data class QueryGraphDescriptor(
    val queries: List<QueryDescriptor>,
    val steps: Map<Int, StepDescriptor>,
    val connections: List<PortConnection>
) {
    fun initStepIds() {
        steps.forEach { it.value.id = it.key }
    }

    fun createRootQuery(): UnboundQuery<*, *, *> {
        return QueryDeserializationContext(this).createQueries().first()
    }
}

@Serializable
data class QueryDescriptor(
    val input: Int,
    val output: Int,
    val sharedSteps: List<Int> = emptyList(),
    val isFluxOutput: Boolean = false,
    val queryId: Long
)

@Serializable
abstract class StepDescriptor {
    @Transient
    var id: Int? = null
    var owner: QueryId? = null
    abstract fun createStep(context: QueryDeserializationContext): IStep
}

@Serializable
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
    initiallyProvidedQuery: Q?,
    queryId: Long?,
    private val queryInitializer: (() -> Q)?
) : IQueryReference<Q> {
    var providedQuery: Q? = initiallyProvidedQuery
        set(value) {
            check(field == null) { "Cannot change providedQuery to $value, it is already set to $field" }
            field = value
        }
    var queryId: Long? = queryId
        set(value) {
            check(field == null) { "Cannot set ID to $value, it is already set to $field" }
            field = value
        }
    private val creatingStacktrace = Exception()
    override val query: Q by lazy {
        providedQuery
            ?: (queryInitializer ?: throw IllegalStateException("query for ID $queryId not found", creatingStacktrace)).invoke()
            ?: throw RuntimeException("Query initializer returned null: $queryInitializer")
    }
    override fun getId(): Long = queryId ?: query.reference.takeIf { it != this }?.getId() ?: throw RuntimeException("ID not set")

    companion object {
        val CONTEXT_VALUE = ContextValue<QueryReference<*>>()
    }
}

class QueryDeserializationContext(val graphDescriptor: QueryGraphDescriptor) {
    init {
        graphDescriptor.initStepIds()
    }
    private val id2stepDesc = graphDescriptor.steps
    private val id2queryDesc = graphDescriptor.queries.associateBy { it.queryId }
    private val createdSteps = HashMap<Int, IStep>()
    private val createdQueryReferences = HashMap<QueryId, QueryReference<*>>()
    private val createdQueries = HashMap<QueryId, UnboundQuery<*, *, *>>()

    private val processedConnections = HashSet<PortConnection>()
    private val consumerId2connections = graphDescriptor.connections.groupBy { it.consumer.step }
    private val producerId2connections = graphDescriptor.connections.groupBy { it.producer.step }

    fun createQueries(): List<UnboundQuery<*, *, *>> {
        graphDescriptor.steps.keys.forEach { getOrCreateStep(it) }
        createConnections()
        checkConnectionOrder()
        val result = graphDescriptor.queries.map { getOrCreateQuery(it.queryId) }
        result.forEach { it.validate() }
        return result
    }

    private fun checkConnectionOrder() {
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

    private fun collectConnectedSteps(stepId: Int, acc: MutableSet<Int>) {
        if (acc.contains(stepId)) return
        acc.add(stepId)
        consumerId2connections[stepId]?.forEach { collectConnectedSteps(it.producer.step, acc) }
        producerId2connections[stepId]?.forEach { collectConnectedSteps(it.consumer.step, acc) }
    }

    fun createConnections() {
        val unprocessedConnections = graphDescriptor.connections.toSet() - processedConnections

        for (connection in unprocessedConnections.sortedBy { it.consumer.port }) {
            val producer = getOrCreateStep(connection.producer.step) as IProducingStep<Any?>
            val consumer = getOrCreateStep(connection.consumer.step) as IConsumingStep<Any?>
            consumer.connect(producer)
            processedConnections += connection
        }
    }

    fun getOrCreateStep(id: Int): IStep {
        return createdSteps.getOrCompute(id) {
            val desc = id2stepDesc[id]
            requireNotNull(desc) { "Step $id not found" }
            QueryReference.CONTEXT_VALUE.computeWith(getOrCreateQueryReference(requireNotNull(desc.owner) { "$desc has no queryId" })) {
                desc.createStep(this)
            }
        }
    }

    fun getOrCreateQueryReference(id: QueryId): QueryReference<*> {
        return createdQueryReferences.getOrCompute(id) {
            QueryReference(null, id, null)
        }
    }

    fun getOrCreateQuery(id: QueryId): UnboundQuery<*, *, *> {
        return createdQueries.getOrCompute(id) {
            val desc = id2queryDesc[id]
            requireNotNull(desc) { "Query $id not found" }
            val inputStep = getOrCreateStep(desc.input) as QueryInput<Any?>
            val outputStep = getOrCreateStep(desc.output) as IProducingStep<Any?>
            val sharedSteps = desc.sharedSteps.map { getOrCreateStep(it) as SharedStep<Any?> }

//            val stepsInQuery = HashSet<Int>()
//            collectConnectedSteps(desc.output, stepsInQuery)
//            collectConnectedSteps(desc.input, stepsInQuery)
//            stepsInQuery.filter { !createdSteps.isComputing(it) }.forEach { getOrCreateStep(it) }

            val reference = getOrCreateQueryReference(id)

            // Some steps implement IMonoStep and IFluxStep. An instanceOf check doesn't work.
            if (desc.isFluxOutput) {
                FluxUnboundQuery<Any?, Any?>(
                    inputStep,
                    outputStep as IFluxStep<Any?>,
                    reference as QueryReference<IFluxUnboundQuery<Any?, Any?>>,
                    sharedSteps
                )
            } else {
                MonoUnboundQuery<Any?, Any?>(
                    inputStep,
                    outputStep as IMonoStep<Any?>,
                    reference as QueryReference<UnboundQuery<Any?, Any?, Any?>>,
                    sharedSteps
                )
            }
        }
    }
}

private val COMPUTING_VALUE = Any()
private fun <K, V> MutableMap<K, V>.getOrCompute(key: K, body: () -> V): V = getOrCompute(key, body, {})
private fun <K, V> MutableMap<K, V>.getOrCompute(key: K, body: () -> V, afterPut: (V) -> Unit): V {
    if (containsKey(key)) {
        val value = get(key)
        check(value != COMPUTING_VALUE) { "Already computing value for $key" }
        return value as V
    }
    try {
        put(key, COMPUTING_VALUE as V)
        val value = body()
        put(key, value)
        afterPut(value)
        return value
    } finally {
        if (get(key) == org.modelix.modelql.core.COMPUTING_VALUE) remove(key)
    }
}
private fun <K, V> MutableMap<K, V>.getIfNotComputing(key: K): V? = get(key).takeIf { it != COMPUTING_VALUE }
private fun <K, V> MutableMap<K, V>.isComputing(key: K): Boolean = get(key) == COMPUTING_VALUE
