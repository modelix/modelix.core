package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName

class ToStringStep : MonoTransformingStep<Any?, String?>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<String?> {
        return serializersModule.serializer<String>().nullable
    }

    override fun createFlow(input: Flow<Any?>, context: IFlowInstantiationContext): Flow<String?> {
        return input.map { it?.toString() }
    }

    override fun createDescriptor(): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("toString")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return ToStringStep()
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.asString()"
    }
}

@JvmName("asStringNullable")
fun IMonoStep<Any?>.asString(): IMonoStep<String?> = ToStringStep().connectAndDowncast(this)
fun IMonoStep<Any>.asString(): IMonoStep<String> = ToStringStep().connectAndDowncast(this) as IMonoStep<String>

@JvmName("asStringNullable")
fun IFluxStep<Any?>.asString(): IFluxStep<String?> = ToStringStep().connectAndDowncast(this)
fun IFluxStep<Any>.asString(): IFluxStep<String> = ToStringStep().connectAndDowncast(this) as IFluxStep<String>
