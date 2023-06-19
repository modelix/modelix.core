package org.modelix.modelql.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class QueryDescriptor(
    val steps: List<StepDescriptor>,
    val connections: List<PortConnection>,
    val input: Int,
    val output: Int
) {
    fun createQuery(): UnboundQuery<*, *> {
        val createdSteps = ArrayList<IStep>()
        fun resolveStep(id: Int): IStep {
            return createdSteps[id]
        }
        steps.forEach { createdSteps += it.createStep() }
        for (connection in connections.sortedBy { it.consumer.port }) {
            val fromStep = resolveStep(connection.producer.step) as IProducingStep<Any?>
            val toStep = resolveStep(connection.consumer.step) as IConsumingStep<Any?>
            toStep.connect(fromStep)
        }
        val inputStep = resolveStep(input) as QueryInput<Any?>
        val outputStep = resolveStep(output) as IProducingStep<Any?>
        return UnboundQuery<Any?, Any?>(inputStep, outputStep)
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
