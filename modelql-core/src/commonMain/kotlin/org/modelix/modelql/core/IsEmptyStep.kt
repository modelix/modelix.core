package org.modelix.modelql.core

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.take
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class IsEmptyStep() : AggregationStep<Any?, Boolean>() {
    override suspend fun aggregate(input: StepFlow<Any?>): IStepOutput<Boolean> {
        return input.take(1).map { false }.onEmpty { emit(false) }.single().asStepOutput(this)
    }

    override fun aggregate(input: Sequence<IStepOutput<Any?>>): IStepOutput<Boolean> = input.none().asStepOutput(this)

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("isEmpty")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IsEmptyStep()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "${getProducers().single()}.isEmpty()"
    }
}

fun IProducingStep<Any?>.isEmpty(): IMonoStep<Boolean> = IsEmptyStep().connectAndDowncast(this)
fun IProducingStep<Any?>.isNotEmpty(): IMonoStep<Boolean> = !isEmpty()
