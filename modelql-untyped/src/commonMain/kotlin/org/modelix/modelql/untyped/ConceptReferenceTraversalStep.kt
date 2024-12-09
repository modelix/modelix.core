package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.serializer
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
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
import org.modelix.modelql.untyped.AllReferencesTraversalStep.Descriptor

class ConceptReferenceTraversalStep() : SimpleMonoTransformingStep<INode?, ConceptReference?>() {
    override fun transform(evaluationContext: QueryEvaluationContext, input: INode?): ConceptReference? {
        return input?.getConceptReference() as ConceptReference?
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<ConceptReference?>> {
        return serializationContext.serializer<ConceptReference>().nullable.stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    override fun toString(): String {
        return "${getProducers().single()}\n.conceptReference()"
    }

    @Serializable
    @SerialName("untyped.conceptReference")
    class Descriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ConceptReferenceTraversalStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}

fun IMonoStep<INode?>.conceptReference(): IMonoStep<ConceptReference?> = ConceptReferenceTraversalStep().connectAndDowncast(this)
fun IFluxStep<INode?>.conceptReference() = ConceptReferenceTraversalStep().connectAndDowncast(this)
