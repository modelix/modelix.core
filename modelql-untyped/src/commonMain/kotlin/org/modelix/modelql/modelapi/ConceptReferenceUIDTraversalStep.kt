package org.modelix.modelql.modelapi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.ConceptReference
import org.modelix.modelql.core.*

class ConceptReferenceUIDTraversalStep(): MonoTransformingStep<ConceptReference, String>() {
    override fun transform(element: ConceptReference): Sequence<String> {
        return sequenceOf(element.getUID())
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<String> {
        return serializersModule.serializer<String>()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("conceptReference.uid")
    class Descriptor() : StepDescriptor() {
        override fun createStep(): IStep {
            return ConceptReferenceUIDTraversalStep()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.getUID()"""
    }
}

fun IMonoStep<ConceptReference>.getUID(): IMonoStep<String> = ConceptReferenceUIDTraversalStep().also { connect(it) }
fun IFluxStep<ConceptReference>.getUID(): IFluxStep<String> = map { it.getUID() }
