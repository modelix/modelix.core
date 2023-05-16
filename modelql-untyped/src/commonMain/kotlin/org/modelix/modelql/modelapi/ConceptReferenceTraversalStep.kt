package org.modelix.modelql.modelapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.modelql.core.*

class ConceptReferenceTraversalStep(): MonoTransformingStep<INode?, ConceptReference?>() {
    override fun transform(element: INode?): Sequence<ConceptReference?> {
        return sequenceOf(element?.getConceptReference() as ConceptReference?)
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<ConceptReference?> {
        return serializersModule.serializer<ConceptReference>().nullable
    }

    override fun createDescriptor() = Descriptor()

    override fun toString(): String {
        return "${getProducers().single()}.conceptReference()"
    }

    @Serializable
    @SerialName("untyped.conceptReference")
    class Descriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return ConceptReferenceTraversalStep()
        }
    }
}


fun IMonoStep<INode?>.conceptReference(): IMonoStep<ConceptReference?> = ConceptReferenceTraversalStep().also { connect(it) }
fun IFluxStep<INode?>.conceptReference(): IFluxStep<ConceptReference?> = map { it.conceptReference() }
