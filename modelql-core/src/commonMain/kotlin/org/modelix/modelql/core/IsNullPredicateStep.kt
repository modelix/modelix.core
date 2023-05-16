package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class IsNullPredicateStep() : MonoTransformingStep<Any?, Boolean>() {
    override fun transform(element: Any?): Sequence<Boolean> {
        return sequenceOf(element == null)
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Boolean> {
        return serializersModule.serializer<Boolean>()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("isNull")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return IsNullPredicateStep()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.isNull()"""
    }
}

fun IMonoStep<Any?>.isNull(): IMonoStep<Boolean> = IsNullPredicateStep().also { connect(it) }
fun <T : Any> IMonoStep<T?>.filterNotNull(): IMonoStep<T> = filter { !it.isNull() } as IMonoStep<T>
fun <T : Any> IFluxStep<T?>.filterNotNull(): IFluxStep<T> = filter { !it.isNull() } as IFluxStep<T>