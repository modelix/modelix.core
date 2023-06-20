package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class InPredicate(val values: Set<String>) : MonoTransformingStep<String?, Boolean>() {

    override fun createFlow(input: Flow<String?>, context: IFlowInstantiationContext): Flow<Boolean> {
        return input.map { values.contains(it) }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Boolean> {
        return serializersModule.serializer<Boolean>()
    }

    override fun createDescriptor() = Descriptor(values)

    @Serializable
    @SerialName("in")
    class Descriptor(val values: Set<String>) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return InPredicate(values)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.inSet(${values.joinToString(", ") { "\"$it\"" }})"""
    }
}

fun IMonoStep<String?>.inSet(values: Set<String>) = InPredicate(values).connectAndDowncast(this)
fun IFluxStep<String?>.inSet(values: Set<String>) = InPredicate(values).connectAndDowncast(this)
