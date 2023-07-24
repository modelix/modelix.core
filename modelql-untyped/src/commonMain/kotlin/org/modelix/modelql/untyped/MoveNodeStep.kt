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
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.TransformingStepWithParameter
import org.modelix.modelql.core.connect

class MoveNodeStep(val role: String?, val index: Int) :
    TransformingStepWithParameter<INode, INode, INode, INode>() {

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<INode>> {
        return getInputProducer().getOutputSerializer(serializersModule)
    }

    override fun validate() {
        super.validate()
        require(!getParameterProducer().canBeEmpty()) { "The child parameter for moveChild is mandatory, but was: ${getParameterProducer()}" }
    }

    override fun transformElement(input: INode, parameter: INode?): INode {
        input.moveChild(input.resolveChildLinkOrFallback(role), index, parameter!!)
        return input
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
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
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MoveNodeStep(role, index)
        }
    }
}

fun IMonoStep<INode>.moveChild(link: String?, index: Int = -1, child: IMonoStep<INode>): IMonoStep<INode> {
    return MoveNodeStep(link, index).also {
        connect(it)
        child.connect(it)
    }
}

fun IMonoStep<INode>.moveChild(link: IChildLink, index: Int = -1, child: IMonoStep<INode>): IMonoStep<INode> {
    return moveChild(link.key(), index, child)
}
