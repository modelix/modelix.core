package org.modelix.modelql.core

import kotlinx.coroutines.flow.take
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class FirstElementStep<E>() : MonoTransformingStep<E, E>() {
    override fun canBeMultiple(): Boolean = false

    override fun createFlow(input: StepFlow<E>, context: IFlowInstantiationContext): StepFlow<E> {
        return input.take(1)
    }

    override fun requiresSingularQueryInput(): Boolean = true

    override fun transform(evaluationContext: QueryEvaluationContext, input: E): E {
        return input
    }

    override fun toString(): String {
        return getProducer().toString() + ".first()"
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<E>> {
        return getProducer().getOutputSerializer(serializersModule)
    }

    override fun createDescriptor(context: QuerySerializationContext) = FirstElementDescriptor()

    @Serializable
    @SerialName("first")
    class FirstElementDescriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return FirstElementStep<Any?>()
        }
    }
}

fun <Out> IProducingStep<Out>.first(): IMonoStep<Out> {
    return FirstElementStep<Out>().also { it.connect(this) }
}
