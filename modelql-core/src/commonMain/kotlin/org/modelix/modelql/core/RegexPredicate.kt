package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class RegexPredicate(val regex: Regex) : MonoTransformingStep<String?, Boolean>() {
    override fun createFlow(input: Flow<String?>, context: IFlowInstantiationContext): Flow<Boolean> {
        return input.map { it?.matches(regex) ?: false }
    }

    override fun transform(input: String?): Boolean {
        return input?.matches(regex) ?: false
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Boolean> {
        return serializersModule.serializer<Boolean>()
    }

    override fun createDescriptor() = Descriptor(regex.pattern)

    @Serializable
    @SerialName("regex")
    class Descriptor(val pattern: String) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return RegexPredicate(Regex(pattern))
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.matches(/${regex.pattern}/)"
    }
}

fun IMonoStep<String?>.matches(regex: Regex): IMonoStep<Boolean> = RegexPredicate(regex).also { connect(it) }
