package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class InPredicate(val values: Set<String>) : MonoTransformingStep<String?, Boolean>() {
    override fun transform(element: String?): Sequence<Boolean> {
        return sequenceOf(values.contains(element))
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

fun IMonoStep<String?>.inSet(values: Set<String>): IMonoStep<Boolean> = InPredicate(values).also { connect(it) }
fun IFluxStep<String?>.inSet(values: Set<String>): IFluxStep<Boolean> = map { it.inSet(values) }
