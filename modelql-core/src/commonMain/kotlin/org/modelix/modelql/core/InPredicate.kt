package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class InPredicate(val values: Set<String>) : MonoTransformingStep<String?, Boolean>() {

    override fun transform(input: String?): Boolean {
        return values.contains(input)
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer()
    }

    override fun createDescriptor(context: QuerySerializationContext) = Descriptor(values)

    @Serializable
    @SerialName("in")
    class Descriptor(val values: Set<String>) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return InPredicate(values)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.inSet(${values.joinToString(", ") { "\"$it\"" }})"""
    }
}

fun IMonoStep<String?>.inSet(values: Set<String>) = InPredicate(values).connectAndDowncast(this)
fun IFluxStep<String?>.inSet(values: Set<String>) = InPredicate(values).connectAndDowncast(this)
