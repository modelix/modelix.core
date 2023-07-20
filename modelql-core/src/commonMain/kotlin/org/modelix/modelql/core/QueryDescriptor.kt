package org.modelix.modelql.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class QueryDescriptor(
    val steps: List<StepDescriptor>,
    val connections: List<PortConnection>,
    val input: Int,
    val output: Int,
    val isFluxOutput: Boolean,
    val queryId: Long
) {
    fun createQuery(): UnboundQuery<*, *, *> {
        val context = QueryDeserializationContext()
        val query = createQuery(context)
        context.resolveQueryReferences()
        return query
    }

    fun createQuery(context: QueryDeserializationContext): UnboundQuery<*, *, *> {
        val createdSteps = ArrayList<IStep>()
        fun resolveStep(id: Int): IStep {
            return createdSteps[id]
        }
        steps.forEach { createdSteps += it.createStep(context) }
        for (connection in connections.sortedBy { it.consumer.port }) {
            val producer = resolveStep(connection.producer.step) as IProducingStep<Any?>
            val consumer = resolveStep(connection.consumer.step) as IConsumingStep<Any?>
            consumer.connect(producer)
        }

        // validate connection order
        for (connection in connections) {
            val producer = resolveStep(connection.producer.step) as IProducingStep<Any?>
            val consumer = resolveStep(connection.consumer.step) as IConsumingStep<Any?>
            val expectedPortAtConsumer = connection.consumer.port
            val actualPortAtConsumer = consumer.getProducers().indexOf(producer)
            if (expectedPortAtConsumer != actualPortAtConsumer) {
                throw RuntimeException("wrong connection order: $consumer")
            }
        }

        val inputStep = resolveStep(input) as QueryInput<Any?>
        val outputStep = resolveStep(output) as IProducingStep<Any?>

        // Some steps implement IMonoStep and IFluxStep. An instanceOf check doesn't work.
        return if (isFluxOutput) {
            FluxUnboundQuery<Any?, Any?>(inputStep, outputStep as IFluxStep<Any?>, queryId)
        } else {
            MonoUnboundQuery<Any?, Any?>(inputStep, outputStep as IMonoStep<Any?>, queryId)
        }.also { context.register(it) }
    }
}

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

class QueryReference<Q : IUnboundQuery<*, *, *>>(
    var providedQuery: Q?,
    private var queryId: Long?,
    private val queryInitializer: (() -> Q)?
) {
    private val creatingStacktrace = Exception()
    val query: Q by lazy {
        providedQuery ?: (queryInitializer ?: throw IllegalStateException("query for ID $queryId not found", creatingStacktrace)).invoke()
    }
    fun getId(): Long = queryId ?: query.id
}

class QuerySerializationContext {
    private val queries = HashMap<Long, UnboundQuery<Any?, Any?, Any?>>()
    fun hasQuery(id: Long): Boolean = queries.containsKey(id)
    fun registerQuery(query: UnboundQuery<*, *, *>) {
        queries[query.id] = query as UnboundQuery<Any?, Any?, Any?>
    }
}

class QueryDeserializationContext {
    private val queryReferences = ArrayList<QueryReference<IUnboundQuery<Any?, Any?, Any?>>>()
    private val queries = ArrayList<UnboundQuery<Any?, Any?, Any?>>()

    fun register(ref: QueryReference<*>) {
        queryReferences += ref as QueryReference<IUnboundQuery<Any?, Any?, Any?>>
    }

    fun register(query: UnboundQuery<*, *, *>) {
        queries += query as UnboundQuery<Any?, Any?, Any?>
    }

    fun resolveQueryReferences() {
        val id2query = queries.associateBy { it.id }
        queryReferences.forEach {
            it.providedQuery = id2query[it.getId()]
                ?: throw RuntimeException("query not found: ${it.getId()}")
        }
    }
}
