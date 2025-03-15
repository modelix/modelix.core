package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class IsNullPredicateStep<In>() : SimpleMonoTransformingStep<In, Boolean>() {

    override fun transform(evaluationContext: QueryEvaluationContext, input: In): Boolean {
        return input == null
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Boolean>> {
        return serializationContext.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("isNull")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IsNullPredicateStep<Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.isNull()"
    }
}

fun <T> IMonoStep<T>.isNull(): IMonoStep<Boolean> = IsNullPredicateStep<T>().also { connect(it) }
fun <T : Any> IMonoStep<T?>.filterNotNull(): IMonoStep<T> = filter { !it.isNull() } as IMonoStep<T>
fun <T : Any> IFluxStep<T?>.filterNotNull(): IFluxStep<T> = filter { !it.isNull() } as IFluxStep<T>
