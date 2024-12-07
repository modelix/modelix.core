package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.NothingSerializer

class MapAccessStep<K, V>() : TransformingStepWithParameter<Map<K, V>, K, Any?, V?>() {

    override fun transformElement(input: IStepOutput<Map<K, V>>, parameter: IStepOutput<K>?): IStepOutput<V?> {
        if (parameter == null) return MultiplexedOutput(1, (null as V?).asStepOutput(this))
        if (input is CollectorStepOutput<*, *, *>) {
            val collectorStepOutput = input as CollectorStepOutput<IZip2Output<Any?, K, V>, Map<K, IStepOutput<V>>, Map<K, V>>
            collectorStepOutput.internalCollection[parameter.value]?.let { return MultiplexedOutput<V?>(0, it) }
        } else {
            input.value[parameter.value]?.let { return MultiplexedOutput<V?>(2, it.asStepOutput(this)) }
        }
        return MultiplexedOutput<V?>(1, (null as V?).asStepOutput(this))
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<V?>> {
        val mapSerializer = MapCollectorStepOutputSerializer.cast(getInputProducer().getOutputSerializer(serializationContext))
        val valueSerializer = mapSerializer.valueSerializer()
        return MultiplexedOutputSerializer<V?>(
            this,
            listOf<KSerializer<IStepOutput<V?>>>(
                valueSerializer as KSerializer<IStepOutput<V?>>,
                nullSerializer<V>().stepOutputSerializer(this) as KSerializer<IStepOutput<V?>>,
                (NothingSerializer() as KSerializer<V?>).stepOutputSerializer(this).upcast(),
            ),
        )
    }

    override fun toString(): String {
        return "${getInputProducer()}\n.get(${getParameterProducer()})"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("map.get")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MapAccessStep<Any?, Any?>()
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor()
    }
}

operator fun <K, V> IMonoStep<Map<K, V>>.get(operand: IMonoStep<K>): IMonoStep<V?> = MapAccessStep<K, V>().also {
    connect(it)
    operand.connect(it)
}
