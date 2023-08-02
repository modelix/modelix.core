package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class RegexPredicate(val regex: Regex) : MonoTransformingStep<String?, Boolean>() {

    override fun transform(evaluationContext: QueryEvaluationContext, input: String?): Boolean {
        return input?.matches(regex) ?: false
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(regex.pattern)

    @Serializable
    @SerialName("regex")
    class Descriptor(val pattern: String) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return RegexPredicate(Regex(pattern))
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.matches(/${regex.pattern}/)"
    }
}

fun IMonoStep<String?>.matches(regex: Regex): IMonoStep<Boolean> = RegexPredicate(regex).also { connect(it) }
