package org.modelix.modelql.modelapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.modelql.core.*

class RoleInParentTraversalStep(): MonoTransformingStep<INode, String?>() {
    override fun transform(element: INode): Sequence<String?> {
        return sequenceOf(element.roleInParent)
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<*> {
        return serializersModule.serializer<String>().nullable
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("untyped.roleInParent")
    class Descriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return RoleInParentTraversalStep()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.roleInParent()"""
    }
}

fun IMonoStep<INode>.roleInParent(): IMonoStep<String?> = RoleInParentTraversalStep().also { connect(it) }
fun IFluxStep<INode>.roleInParent(): IFluxStep<String?> = map { it.roleInParent() }
