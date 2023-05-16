package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class ZipElementAccessStep_first<RemoteE> : MonoTransformingStep<IZip1Output<*, RemoteE>, RemoteE>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<RemoteE> {
        val zipSerializer = getProducers().single().getOutputSerializer(serializersModule) as ZipOutputSerializer<RemoteE>
        return zipSerializer.elementSerializers[0]
    }

    override fun transform(element: IZip1Output<*, RemoteE>): Sequence<RemoteE> {
        return sequenceOf(element.first)
    }

    override fun createDescriptor(): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("zip.output.first")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return ZipElementAccessStep_first<Any?>()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.first"""
    }
}
class ZipElementAccessStep_second<RemoteE> : MonoTransformingStep<IZip2Output<*, *, RemoteE>, RemoteE>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<RemoteE> {
        val zipSerializer = getProducers().single().getOutputSerializer(serializersModule) as ZipOutputSerializer<RemoteE>
        return zipSerializer.elementSerializers[1]
    }

    override fun transform(element: IZip2Output<*, *, RemoteE>): Sequence<RemoteE> {
        return sequenceOf(element.second)
    }


    override fun createDescriptor(): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("zip.output.second")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return ZipElementAccessStep_second<Any?>()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.second"""
    }
}

// TODO steps for third, fourth, ...

val <RemoteE> IMonoStep<IZip1Output<*, RemoteE>>.first: IMonoStep<RemoteE>
    get() = ZipElementAccessStep_first<RemoteE>().also { connect(it) }
val <RemoteE> IMonoStep<IZip2Output<*, *, RemoteE>>.second: IMonoStep<RemoteE>
    get() = ZipElementAccessStep_second<RemoteE>().also { connect(it) }

