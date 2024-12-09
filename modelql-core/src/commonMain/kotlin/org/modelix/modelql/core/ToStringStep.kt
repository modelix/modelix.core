package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName

class ToStringStep : SimpleMonoTransformingStep<Any?, String?>() {
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<String?>> {
        return serializationContext.serializer<String>().nullable.stepOutputSerializer(this)
    }

    override fun transform(evaluationContext: QueryEvaluationContext, input: Any?): String? {
        return input?.toString()
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("toString")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ToStringStep()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.asString()"
    }
}

@JvmName("asStringNullable")
fun IMonoStep<Any?>.asString(): IMonoStep<String?> = ToStringStep().connectAndDowncast(this)
fun IMonoStep<Any>.asString(): IMonoStep<String> = ToStringStep().connectAndDowncast(this) as IMonoStep<String>

@JvmName("asStringNullable")
fun IFluxStep<Any?>.asString(): IFluxStep<String?> = ToStringStep().connectAndDowncast(this)
fun IFluxStep<Any>.asString(): IFluxStep<String> = ToStringStep().connectAndDowncast(this) as IFluxStep<String>
