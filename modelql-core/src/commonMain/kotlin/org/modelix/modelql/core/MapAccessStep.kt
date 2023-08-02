package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class MapAccessStep<K, V>() : TransformingStepWithParameter<Map<K, V>, K, Any?, V?>() {

    override fun transformElement(input: IStepOutput<Map<K, V>>, parameter: IStepOutput<K>?): IStepOutput<V?> {
        if (parameter == null) return MultiplexedOutput(1, (null as V?).asStepOutput(this))
        if (input is CollectorStepOutput<*, *, *>) {
            val collectorStepOutput = input as CollectorStepOutput<IZip2Output<Any?, K, V>, HashMap<K, IStepOutput<V>>, Map<K, V>>
            collectorStepOutput.internalCollection[parameter.value]?.let { return MultiplexedOutput<V?>(0, it) }
        } else {
            input.value[parameter.value]?.let { return MultiplexedOutput<V?>(0, it.asStepOutput(this)) }
        }
        return MultiplexedOutput<V?>(1, (null as V?).asStepOutput(this))
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<V?>> {
        val mapSerializer = getInputProducer().getOutputSerializer(serializersModule) as MapCollectorStepOutputSerializer<K, V>
        val zipSerializer = mapSerializer.inputElementSerializer as ZipOutputSerializer<*, *>
        val valueSerializer = zipSerializer.elementSerializers[1] as KSerializer<IStepOutput<V>>
        return MultiplexedOutputSerializer<V?>(
            this,
            listOf(
                valueSerializer as KSerializer<IStepOutput<V?>>,
                nullSerializer<V>().stepOutputSerializer(this) as KSerializer<IStepOutput<V?>>
            )
        )
    }

    override fun toString(): String {
        return "${getInputProducer()}.get(${getParameterProducer()})"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("map.get")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return MapAccessStep<Any?, Any?>()
        }
    }
}

operator fun <K, V> IMonoStep<Map<K, V>>.get(operand: IMonoStep<K>): IMonoStep<V?> = MapAccessStep<K, V>().also {
    connect(it)
    operand.connect(it)
}
