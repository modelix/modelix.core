package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class StringContainsPredicate(val substring: String) : MonoTransformingStep<String?, Boolean>() {
    override fun createFlow(input: Flow<String?>, context: IFlowInstantiationContext): Flow<Boolean> {
        return input.map { it?.contains(substring) ?: false }
    }

    override fun transform(input: String?): Boolean {
        return input?.contains(substring) ?: false
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

fun IMonoStep<String?>.contains(substring: String) = StringContainsPredicate(substring).connectAndDowncast(this)
