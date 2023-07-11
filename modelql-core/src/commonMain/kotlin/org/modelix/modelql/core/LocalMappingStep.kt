package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlin.experimental.ExperimentalTypeInference

open class LocalMappingStep<In, Out>(val transformation: (In) -> Out) : MonoTransformingStep<In, Out>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out>> {
        return LocalMappingSerializer(this, getProducer().getOutputSerializer(serializersModule)).stepOutputSerializer()
    }

    override fun transform(input: In): Out {
        return transformation(input)
    }

    override fun createDescriptor(): StepDescriptor {
        return IdentityStep.IdentityStepDescriptor()
    }

    override fun toString(): String {
        return "${getProducer()}.mapLocal()"
    }
}

private class LocalMappingBuilder<In, Out> : ILocalMappingBuilder<In, Out> {
    private val valueRequests = ArrayList<AsyncBuilder.ValueRequest<Any?>>()
    private var resultHandler: (() -> Out)? = null

    fun compileOutputStep(): IMonoStep<IZipOutput<*>> {
        val allRequestSteps: List<IMonoStep<Any?>> = valueRequests.map { it.step }
        return zipList(*allRequestSteps.toTypedArray())
    }

    fun processResult(result: IZipOutput<*>): Out {
        val allRequests: List<AsyncBuilder.Request<Any?>> = valueRequests
        allRequests.zip(result.values).forEach { (request, value) ->
            request.set(value)
        }

        return resultHandler!!.invoke()
    }

    override fun onSuccess(body: () -> Out) {
        resultHandler = body
    }
    override fun <T> IMonoStep<T>.getLater(): AsyncBuilder.ValueRequest<T> {
        return AsyncBuilder.ValueRequest(this).also { valueRequests.add(it as AsyncBuilder.ValueRequest<Any?>) }
    }
}

interface ILocalMappingBuilder<In, Out> {
    @OptIn(ExperimentalTypeInference::class)
    @BuilderInference
    fun onSuccess(body: () -> Out)
    fun <T> IMonoStep<T>.getLater(): IValueRequest<T>
}

class LocalMappingSerializer<In, Out>(val step: LocalMappingStep<In, Out>, val inputSerializer: KSerializer<out IStepOutput<In>>) : KSerializer<Out> {
    override fun deserialize(decoder: Decoder): Out {
        return step.transformation(decoder.decodeSerializableValue(inputSerializer).value)
    }

    override val descriptor: SerialDescriptor
        get() = inputSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Out) {
        throw UnsupportedOperationException("Local mappings are applied after receiving the query result. Their output is not expected to be serialized.")
    }
}

class ExecuteLocalStep<In, Out>(transformation: (In) -> Out) : LocalMappingStep<In, Out>(transformation) {
    override fun hasSideEffect(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}.executeLocal()"
    }
}

fun <In, Out> IFluxStep<In>.mapLocal2(body: ILocalMappingBuilder<In, Out>.(IMonoStep<In>) -> Unit): IFluxStep<Out> {
    return map { it.mapLocal2(body) }
}

fun <In, Out> IMonoStep<In>.mapLocal2(body: ILocalMappingBuilder<In, Out>.(IMonoStep<In>) -> Unit): IMonoStep<Out> {
    val builder = LocalMappingBuilder<In, Out>()
    builder.apply {
        body(this@mapLocal2)
    }
    return builder.compileOutputStep().mapLocal { builder.processResult(it) }
}

fun <In, Out> IMonoStep<In>.mapLocal(body: (In) -> Out) = LocalMappingStep(body).connectAndDowncast(this)
fun <In, Out> IFluxStep<In>.mapLocal(body: (In) -> Out) = LocalMappingStep(body).connectAndDowncast(this)
fun <In, Out> IMonoStep<In>.executeLocal(body: (In) -> Out) = ExecuteLocalStep(body).connectAndDowncast(this)
fun <In, Out> IFluxStep<In>.executeLocal(body: (In) -> Out) = ExecuteLocalStep(body).connectAndDowncast(this)
