package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class StringContainsPredicate(val substring: String) : SimpleMonoTransformingStep<String?, Boolean>() {

    override fun transform(evaluationContext: QueryEvaluationContext, input: String?): Boolean {
        return input?.contains(substring) ?: false
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Boolean>> {
        return serializationContext.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = StringContainsDescriptor(substring)

    @Serializable
    @SerialName("stringContains")
    data class StringContainsDescriptor(val substring: String) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return StringContainsPredicate(substring)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = StringContainsDescriptor(substring)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.contains(\"$substring\")"
    }
}

fun IMonoStep<String?>.contains(substring: String) = StringContainsPredicate(substring).connectAndDowncast(this)
