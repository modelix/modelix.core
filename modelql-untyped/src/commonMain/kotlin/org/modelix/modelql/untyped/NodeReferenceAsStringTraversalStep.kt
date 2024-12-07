package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.modelql.core.IFluxStep
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
import org.modelix.modelql.core.stepOutputSerializer

class NodeReferenceAsStringTraversalStep() : SimpleMonoTransformingStep<INodeReference, String>() {
    override fun transform(evaluationContext: QueryEvaluationContext, input: INodeReference): String {
        return input.serialize()
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<String>> {
        return serializationContext.serializer<String>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("nodeReferenceAsString")
    class Descriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return NodeReferenceAsStringTraversalStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.serialize()"
    }
}

fun IMonoStep<INodeReference>.serialize() = NodeReferenceAsStringTraversalStep().connectAndDowncast(this)
fun IFluxStep<INodeReference>.serialize() = NodeReferenceAsStringTraversalStep().connectAndDowncast(this)

fun IMonoStep<INode>.nodeReferenceAsString(): IMonoStep<String> = nodeReference().serialize()
fun IFluxStep<INode>.nodeReferenceAsString(): IFluxStep<String> = nodeReference().serialize()
