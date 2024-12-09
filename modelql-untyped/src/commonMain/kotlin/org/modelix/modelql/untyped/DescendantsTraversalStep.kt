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

class DescendantsTraversalStep(val includeSelf: Boolean) : FluxTransformingStep<INode, INode>(), IFluxStep<INode> {
    override fun createStream(input: StepStream<INode>, context: IStreamInstantiationContext): StepStream<INode> {
        return input.flatMap { it.value.asAsyncNode().getDescendants(includeSelf) }.map { it.asRegularNode() }.asStepStream(this)
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<INode>> {
        return serializationContext.serializer<INode>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = if (includeSelf) WithSelfDescriptor() else WithoutSelfDescriptor()

    @Serializable
    @SerialName("untyped.descendantsAndSelf")
    class WithSelfDescriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return DescendantsTraversalStep(true)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = WithSelfDescriptor()
    }

    @Serializable
    @SerialName("untyped.descendants")
    class WithoutSelfDescriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return DescendantsTraversalStep(false)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = WithoutSelfDescriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.${if (includeSelf) "descendantsAndSelf" else "descendants"}()"
    }
}

fun IProducingStep<INode>.descendants(includeSelf: Boolean = false): IFluxStep<INode> = DescendantsTraversalStep(includeSelf).also { connect(it) }
