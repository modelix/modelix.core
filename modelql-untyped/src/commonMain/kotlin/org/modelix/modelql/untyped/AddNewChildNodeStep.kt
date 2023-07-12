package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.INode
import org.modelix.model.api.key
import org.modelix.model.api.resolveChildLinkOrFallback
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.MonoTransformingStep
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.connect
import org.modelix.modelql.core.stepOutputSerializer

class AddNewChildNodeStep(val role: String?, val index: Int, val concept: ConceptReference?) :
    MonoTransformingStep<INode, INode>() {

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<INode>> {
        return serializersModule.serializer<INode>().stepOutputSerializer()
    }

    override fun transform(input: INode): INode {
        return input.addNewChild(input.resolveChildLinkOrFallback(role), index, concept)
    }

    override fun createDescriptor(): StepDescriptor {
        return Descriptor(role, index, concept)
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}.addNewChild($role, $index, $concept)"
    }

    @Serializable
    @SerialName("untyped.addNewChild")
    class Descriptor(val role: String?, val index: Int, val concept: ConceptReference?) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return AddNewChildNodeStep(role, index, concept)
        }
    }
}

fun IMonoStep<INode>.addNewChild(role: String, index: Int, concept: ConceptReference?): IMonoStep<INode> {
    return AddNewChildNodeStep(role, index, concept).also { connect(it) }
}
fun IMonoStep<INode>.addNewChild(role: String, concept: ConceptReference?): IMonoStep<INode> {
    return addNewChild(role, -1, concept)
}
fun IMonoStep<INode>.addNewChild(role: String): IMonoStep<INode> {
    return addNewChild(role, -1, null)
}
fun IMonoStep<INode>.addNewChild(role: String, index: Int): IMonoStep<INode> {
    return addNewChild(role, index, null)
}
fun IMonoStep<INode>.addNewChild(role: IChildLink, index: Int, concept: ConceptReference?): IMonoStep<INode> {
    return addNewChild(role.key(), index, concept)
}
fun IMonoStep<INode>.addNewChild(role: IChildLink, concept: ConceptReference?): IMonoStep<INode> {
    return addNewChild(role.key(), -1, concept)
}
fun IMonoStep<INode>.addNewChild(role: IChildLink): IMonoStep<INode> {
    return addNewChild(role.key(), -1, role.targetConcept.getReference() as ConceptReference)
}
fun IMonoStep<INode>.addNewChild(role: IChildLink, index: Int): IMonoStep<INode> {
    return addNewChild(role.key(), index, role.targetConcept.getReference() as ConceptReference)
}
