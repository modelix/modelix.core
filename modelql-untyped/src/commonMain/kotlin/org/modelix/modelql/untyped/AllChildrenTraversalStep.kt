package org.modelix.modelql.untyped

import com.badoo.reaktive.observable.flatMap
import com.badoo.reaktive.observable.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.model.api.INode
import org.modelix.model.api.async.asAsyncNode
import org.modelix.modelql.core.FluxTransformingStep
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IProducingStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.IStreamInstantiationContext
import org.modelix.modelql.core.IdReassignments
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.StepStream
import org.modelix.modelql.core.asStepStream
import org.modelix.modelql.core.connect
import org.modelix.modelql.core.stepOutputSerializer

class AllChildrenTraversalStep() : FluxTransformingStep<INode, INode>() {
    override fun createStream(input: StepStream<INode>, context: IStreamInstantiationContext): StepStream<INode> {
        return input.flatMap { it.value.asAsyncNode().getAllChildren().map { it.asRegularNode() } }.asStepStream(this)
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<INode>> {
        return serializationContext.serializer<INode>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = AllChildrenStepDescriptor()

    @Serializable
    @SerialName("untyped.allChildren")
    class AllChildrenStepDescriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return AllChildrenTraversalStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = AllChildrenStepDescriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.allChildren()"
    }
}

fun IProducingStep<INode>.allChildren(): IFluxStep<INode> = AllChildrenTraversalStep().also { connect(it) }
