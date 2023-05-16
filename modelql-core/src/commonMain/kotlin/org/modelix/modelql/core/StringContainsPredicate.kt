package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class StringContainsPredicate(val substring: String) : MonoTransformingStep<String?, Boolean>() {
    override fun transform(element: String?): Sequence<Boolean> {
        return sequenceOf(if (element == null) false else element.contains(substring))
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Boolean> {
        return serializersModule.serializer<Boolean>()
    }

    override fun createDescriptor() = StringContainsDescriptor(substring)

    @Serializable
    @SerialName("stringContains")
    class StringContainsDescriptor(val substring: String) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return StringContainsPredicate(substring)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.contains("$substring")"""
    }
}

fun IMonoStep<String?>.contains(substring: String): IMonoStep<Boolean> = StringContainsPredicate(substring).also { connect(it) }
