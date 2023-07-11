package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class IsNullPredicateStep<In>() : MonoTransformingStep<In, Boolean>() {

    override fun transform(input: In): Boolean {
        return input == null
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer()
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("isNull")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return IsNullPredicateStep<Any?>()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.isNull()"""
    }
}

fun <T> IMonoStep<T>.isNull(): IMonoStep<Boolean> = IsNullPredicateStep<T>().also { connect(it) }
fun <T : Any> IMonoStep<T?>.filterNotNull(): IMonoStep<T> = filter { !it.isNull() } as IMonoStep<T>
fun <T : Any> IFluxStep<T?>.filterNotNull(): IFluxStep<T> = filter { !it.isNull() } as IFluxStep<T>
