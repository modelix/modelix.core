package org.modelix.modelql.modelapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.modelql.core.*

class PropertyTraversalStep(val role: String): MonoTransformingStep<INode, String?>() {
    override fun transform(element: INode): Sequence<String?> {
        return sequenceOf(element.getPropertyValue(role))
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<String?> {
        return serializersModule.serializer<String?>()
    }

    override fun createDescriptor() = PropertyStepDescriptor(role)

    @Serializable
    @SerialName("untyped.property")
    class PropertyStepDescriptor(val role: String) : StepDescriptor() {
        override fun createStep(): IStep {
            return PropertyTraversalStep(role)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.property("$role")"""
    }
}

fun IMonoStep<INode>.property(role: String): IMonoStep<String?> = PropertyTraversalStep(role).also { connect(it) }
fun IFluxStep<INode>.property(role: String): IFluxStep<String?> = map { it.property(role) }