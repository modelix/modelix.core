package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class StringContainsPredicate(val substring: String) : MonoTransformingStep<String?, Boolean>() {

    override fun transform(evaluationContext: QueryEvaluationContext, input: String?): Boolean {
        return input?.contains(substring) ?: false
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer()
    }

    override fun createDescriptor(context: QuerySerializationContext) = StringContainsDescriptor(substring)

    @Serializable
    @SerialName("stringContains")
    class StringContainsDescriptor(val substring: String) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return StringContainsPredicate(substring)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.contains("$substring")"""
    }
}

fun IMonoStep<String?>.contains(substring: String) = StringContainsPredicate(substring).connectAndDowncast(this)
