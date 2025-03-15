package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class RegexPredicate(val regex: Regex) : SimpleMonoTransformingStep<String?, Boolean>() {

    override fun transform(evaluationContext: QueryEvaluationContext, input: String?): Boolean {
        return input?.matches(regex) ?: false
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Boolean>> {
        return serializationContext.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(regex.pattern)

    @Serializable
    @SerialName("regex")
    data class Descriptor(val pattern: String) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return RegexPredicate(Regex(pattern))
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(pattern)
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.matches(/${regex.pattern}/)"
    }
}

fun IMonoStep<String?>.matches(regex: Regex): IMonoStep<Boolean> = RegexPredicate(regex).also { connect(it) }
