package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class ZipElementAccessStep<Out>(val index: Int) : MonoTransformingStep<IZipOutput<Any?>, Out>() {

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<*> {
        val zipSerializer = getProducers().single().getOutputSerializer(serializersModule) as ZipOutputSerializer<Out>
        return zipSerializer.elementSerializers[index]
    }

    override fun transform(element: IZipOutput<*>): Sequence<Out> {
        return sequenceOf(element.values.get(index) as Out)
    }


    override fun createDescriptor(): StepDescriptor {
        return Descriptor(index)
    }

    @Serializable
    @SerialName("zip.output.access")
    class Descriptor(val index: Int) : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return ZipElementAccessStep<Any?>(index)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}[$index]"""
    }
}

val <RemoteE> IMonoStep<IZip1Output<*, RemoteE>>.first: IMonoStep<RemoteE>
    get() = ZipElementAccessStep<RemoteE>(0).also { connect(it) }
val <RemoteE> IMonoStep<IZip2Output<*, *, RemoteE>>.second: IMonoStep<RemoteE>
    get() = ZipElementAccessStep<RemoteE>(1).also { connect(it) }
val <RemoteE> IMonoStep<IZip3Output<*, *, *, RemoteE>>.third: IMonoStep<RemoteE>
    get() = ZipElementAccessStep<RemoteE>(2).also { connect(it) }
val <RemoteE> IMonoStep<IZip4Output<*, *, *, *, RemoteE>>.forth: IMonoStep<RemoteE>
    get() = ZipElementAccessStep<RemoteE>(3).also { connect(it) }
val <RemoteE> IMonoStep<IZip5Output<*, *, *, *, *, RemoteE>>.fifth: IMonoStep<RemoteE>
    get() = ZipElementAccessStep<RemoteE>(4).also { connect(it) }
val <RemoteE> IMonoStep<IZip6Output<*, *, *, *, *, *, RemoteE>>.sixth: IMonoStep<RemoteE>
    get() = ZipElementAccessStep<RemoteE>(5).also { connect(it) }
val <RemoteE> IMonoStep<IZip7Output<*, *, *, *, *, *, *, RemoteE>>.seventh: IMonoStep<RemoteE>
    get() = ZipElementAccessStep<RemoteE>(6).also { connect(it) }
val <RemoteE> IMonoStep<IZip8Output<*, *, *, *, *, *, *, *, RemoteE>>.eighth: IMonoStep<RemoteE>
    get() = ZipElementAccessStep<RemoteE>(7).also { connect(it) }
val <RemoteE> IMonoStep<IZip9Output<*, *, *, *, *, *, *, *, *, RemoteE>>.ninth: IMonoStep<RemoteE>
    get() = ZipElementAccessStep<RemoteE>(8).also { connect(it) }

