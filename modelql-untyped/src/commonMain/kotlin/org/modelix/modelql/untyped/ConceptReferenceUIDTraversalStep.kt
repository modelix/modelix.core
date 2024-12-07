package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.modelix.model.api.ConceptReference
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
import org.modelix.modelql.core.mapIfNotNull
import org.modelix.modelql.core.stepOutputSerializer
import org.modelix.modelql.untyped.AllReferencesTraversalStep.Descriptor
import kotlin.jvm.JvmName

class ConceptReferenceUIDTraversalStep() : SimpleMonoTransformingStep<ConceptReference, String>() {
    override fun transform(evaluationContext: QueryEvaluationContext, input: ConceptReference): String {
        return input.getUID()
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<String>> {
        return serializationContext.serializer<String>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("conceptReference.uid")
    class Descriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ConceptReferenceUIDTraversalStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.getUID()"
    }
}

fun IMonoStep<ConceptReference>.getUID(): IMonoStep<String> = ConceptReferenceUIDTraversalStep().connectAndDowncast(this)
fun IFluxStep<ConceptReference>.getUID(): IFluxStep<String> = ConceptReferenceUIDTraversalStep().connectAndDowncast(this)

@JvmName("getUID_nullable")
fun IMonoStep<ConceptReference?>.getUID(): IMonoStep<String?> = mapIfNotNull { it.getUID() }

@JvmName("getUID_nullable")
fun IFluxStep<ConceptReference?>.getUID(): IFluxStep<String?> = mapIfNotNull { it.getUID() }
