package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INode
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.IdReassignments
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryEvaluationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.SimpleMonoTransformingStep
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.connect
import org.modelix.modelql.core.stepOutputSerializer

class AddNewChildNodeStep(val link: IChildLinkReference, val index: Int, val concept: ConceptReference?) :
    SimpleMonoTransformingStep<INode, INode>() {

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<INode>> {
        return serializationContext.serializer<INode>().stepOutputSerializer(this)
    }

    override fun transform(evaluationContext: QueryEvaluationContext, input: INode): INode {
        return input.addNewChild(link.toLegacy(), index, concept)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor(link.getIdOrName(), link, index, concept)
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}\n.addNewChild($link, $index, $concept)"
    }

    @Serializable
    @SerialName("untyped.addNewChild")
    data class Descriptor(val role: String?, val link: IChildLinkReference? = null, val index: Int, val concept: ConceptReference?) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return AddNewChildNodeStep(link ?: IChildLinkReference.fromString(role), index, concept)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(role, link, index, concept)
    }
}

fun IMonoStep<INode>.addNewChild(role: IChildLinkReference, index: Int, concept: ConceptReference?): IMonoStep<INode> {
    return AddNewChildNodeStep(role, index, concept).also { connect(it) }
}

fun IMonoStep<INode>.addNewChild(role: IChildLinkReference, concept: ConceptReference?): IMonoStep<INode> {
    return addNewChild(role, -1, concept)
}

@Deprecated("Provide an IChildLinkReference")
fun IMonoStep<INode>.addNewChild(role: String, index: Int, concept: ConceptReference?): IMonoStep<INode> {
    return AddNewChildNodeStep(IChildLinkReference.fromString(role), index, concept).also { connect(it) }
}

@Deprecated("Provide an IChildLinkReference")
fun IMonoStep<INode>.addNewChild(role: String, concept: ConceptReference?): IMonoStep<INode> {
    return addNewChild(IChildLinkReference.fromString(role), -1, concept)
}

@Deprecated("Provide an IChildLinkReference")
fun IMonoStep<INode>.addNewChild(role: String): IMonoStep<INode> {
    return addNewChild(IChildLinkReference.fromString(role), -1, null)
}

@Deprecated("Provide an IChildLinkReference")
fun IMonoStep<INode>.addNewChild(role: String, index: Int): IMonoStep<INode> {
    return addNewChild(IChildLinkReference.fromString(role), index, null)
}

@Deprecated("Provide an IChildLinkReference")
fun IMonoStep<INode>.addNewChild(role: IChildLink, index: Int, concept: ConceptReference?): IMonoStep<INode> {
    return addNewChild(role.toReference(), index, concept)
}

@Deprecated("Provide an IChildLinkReference")
fun IMonoStep<INode>.addNewChild(role: IChildLink, concept: ConceptReference?): IMonoStep<INode> {
    return addNewChild(role.toReference(), -1, concept)
}

@Deprecated("Provide an IChildLinkReference")
fun IMonoStep<INode>.addNewChild(role: IChildLink): IMonoStep<INode> {
    return addNewChild(role.toReference(), -1, role.targetConcept.getReference() as ConceptReference)
}

@Deprecated("Provide an IChildLinkReference")
fun IMonoStep<INode>.addNewChild(role: IChildLink, index: Int): IMonoStep<INode> {
    return addNewChild(role.toReference(), index, role.targetConcept.getReference() as ConceptReference)
}
