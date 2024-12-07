package org.modelix.modelql.untyped

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.modelix.model.api.ConceptReference
import org.modelix.modelql.core.ConstantSourceStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IdReassignments
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.StepDescriptor
import kotlin.reflect.typeOf

class ConceptReferenceSetSourceStep(val referenceSet: Set<ConceptReference>) : ConstantSourceStep<Set<ConceptReference>>(referenceSet, typeOf<Set<ConceptReference>>()) {
    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor = Descriptor(referenceSet)

    @Serializable
    @SerialName("conceptReferenceSetMonoSource")
    data class Descriptor(val referenceSet: Set<ConceptReference>) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ConceptReferenceSetSourceStep(referenceSet)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(referenceSet)
    }

    override fun canEvaluateStatically(): Boolean = true
    override fun evaluateStatically(): Set<ConceptReference> = referenceSet
}

fun Set<ConceptReference>.asMono() = ConceptReferenceSetSourceStep(this)
