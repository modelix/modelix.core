package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import org.modelix.model.api.IChildLink
import org.modelix.model.api.INode
import org.modelix.model.api.key
import org.modelix.model.api.resolveChildLinkOrFallback
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.connect

class MoveNodeStep(val role: String?, val index: Int) :
    TransformingStepWithParameter<INode, INode, INode, INode>() {

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out INode> {
        return getInputProducer().getOutputSerializer(serializersModule)
    }

    override fun transformElement(input: INode, parameter: INode): INode {
        input.moveChild(input.resolveChildLinkOrFallback(role), index, parameter)
        return input
    }

    override fun createDescriptor(): StepDescriptor {
        return Descriptor(role, index)
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}.moveChild($role, $index, ${getParameterProducer()})"
    }

    @Serializable
    @SerialName("untyped.moveChild")
    class Descriptor(val role: String?, val index: Int) : StepDescriptor() {
        override fun createStep(): IStep {
            return MoveNodeStep(role, index)
        }
    }
}

fun IMonoStep<INode>.moveChild(link: IChildLink, index: Int, child: IMonoStep<INode>): IMonoStep<INode> {
    return MoveNodeStep(link.key(), index).also {
        connect(it)
        child.connect(it)
    }
}

fun IMonoStep<INode>.moveChild(link: IChildLink, child: IMonoStep<INode>): IMonoStep<INode> {
    return moveChild(link, -1, child)
}
