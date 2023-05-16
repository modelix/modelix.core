package org.modelix.modelql.modelapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.modelql.core.*

class ReferenceTraversalStep(val role: String): MonoTransformingStep<INode, INode>() {
    override fun transform(element: INode): Sequence<INode> {
        return sequenceOf(element.getReferenceTarget(role)).filterNotNull()
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun createDescriptor() = Descriptor(role)

    @Serializable
    @SerialName("untyped.referenceTarget")
    class Descriptor(val role: String) : StepDescriptor() {
        override fun createStep(): IStep {
            return ReferenceTraversalStep(role)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.reference("$role")"""
    }
}

fun IMonoStep<INode>.reference(role: String): IMonoStep<INode> = ReferenceTraversalStep(role).also { connect(it) }
fun IFluxStep<INode>.reference(role: String): IFluxStep<INode> = map { it.reference(role) }
fun IMonoStep<INode>.referenceOrNull(role: String): IMonoStep<INode?> = reference(role).orNull()
fun IFluxStep<INode>.referenceOrNull(role: String): IFluxStep<INode?> = map { it.referenceOrNull(role) }