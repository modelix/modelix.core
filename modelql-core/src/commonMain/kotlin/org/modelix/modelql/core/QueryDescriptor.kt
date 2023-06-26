package org.modelix.modelql.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class QueryDescriptor(
    val steps: List<StepDescriptor>,
    val connections: List<PortConnection>,
    val input: Int,
    val output: Int,
    val isFluxOutput: Boolean
) {
    fun createQuery(): UnboundQuery<*, *, *> {
        val createdSteps = ArrayList<IStep>()
        fun resolveStep(id: Int): IStep {
            return createdSteps[id]
        }
        steps.forEach { createdSteps += it.createStep() }
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
            FluxUnboundQuery<Any?, Any?>(inputStep, outputStep as IFluxStep<Any?>)
        } else {
            MonoUnboundQuery<Any?, Any?>(inputStep, outputStep as IMonoStep<Any?>)
        }
    }
}

@Serializable
abstract class StepDescriptor {
    @Transient
    var id: Int? = null
    abstract fun createStep(): IStep
}

sealed class CoreStepDescriptor : StepDescriptor()

@Serializable
data class PortConnection(val producer: PortReference, val consumer: PortReference)

@Serializable
data class PortReference(val step: Int, val port: Int = 0)
