package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class MapAccessStep<K, V>() : TransformingStepWithParameter<Map<K, V>, K, Any?, V?>() {

    override fun transformElement(input: Map<K, V>, parameter: K?): V? {
        return input[parameter]
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<V?>> {
        val mapSerializer = getInputProducer().getOutputSerializer(serializersModule) as MapCollectorStepOutputSerializer<K, V>
        val zipSerializer = mapSerializer.inputElementSerializer as ZipOutputSerializer<*, *>
        val valueSerializer = zipSerializer.elementSerializers[1] as KSerializer<IStepOutput<V>>
        return valueSerializer
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
