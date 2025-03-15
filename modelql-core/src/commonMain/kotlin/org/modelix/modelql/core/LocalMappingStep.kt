package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.experimental.ExperimentalTypeInference

open class LocalMappingStep<In, Out>(val transformation: (In) -> Out) : MonoTransformingStep<In, Out>() {
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        return LocalMappingSerializer(this, getProducer().getOutputSerializer(serializationContext)).stepOutputSerializer(this)
    }

    override fun createStream(input: StepStream<In>, context: IStreamInstantiationContext): StepStream<Out> {
        return input.map { transformation(it.value).asStepOutput(this) }
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return IdentityStep.IdentityStepDescriptor()
    }

    override fun toString(): String {
        return "${getProducer()}\n.mapLocal()"
    }
}

private class LocalMappingBuilder<In, Out> : ILocalMappingBuilder<In, Out> {
    private val zipBuilder = ZipBuilder()
    private var resultHandlers = ArrayList<() -> Out>(1)

    fun compileOutputStep(): IMonoStep<IZipOutput<*>> {
        return zipBuilder.compileOutputStep()
    }

    fun compileProcessingOutputStep(): IMonoStep<Out> {
        return compileOutputStep().mapLocal { processResult(it) }
    }

    fun processResult(result: IZipOutput<*>): Out {
        return zipBuilder.withResult(result) {
            // When there are multiple handlers they usually all return Unit.
            // When the output is not Unit, then there is usually only one handler.
            // Returning a list here would add unnecessary complexity.
            resultHandlers.map { it.invoke() }.toSet().single()
        }
    }

    override fun onSuccess(body: () -> Out) {
        resultHandlers += body
    }

    override fun <T> IMonoStep<T>.request(): IValueRequest<T> {
        return zipBuilder.request(this)
    }
}

interface IValueRequest<out E> {
    fun get(): E
}

interface IZipBuilderContext {
    @Deprecated("use request()", ReplaceWith("request()"))
    fun <T> IMonoStep<T>.getLater(): IValueRequest<T> = request()
    fun <T> IMonoStep<T>.request(): IValueRequest<T>
}

interface ILocalMappingBuilder<In, Out> : IZipBuilderContext {
    @OptIn(ExperimentalTypeInference::class)
    @BuilderInference
    fun onSuccess(body: () -> Out)
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
        return "${getProducer()}\n.executeLocal()"
    }
}

@Deprecated("renamed to buildLocalMapping", ReplaceWith("buildLocalMapping(body)"))
fun <In, Out> IFluxStep<In>.mapLocal2(body: ILocalMappingBuilder<In, Out>.(IMonoStep<In>) -> Unit): IFluxStep<Out> {
    return buildLocalMapping(body)
}

fun <In, Out> IFluxStep<In>.buildLocalMapping(body: ILocalMappingBuilder<In, Out>.(IMonoStep<In>) -> Unit): IFluxStep<Out> {
    return map { it.buildLocalMapping(body) }
}

@Deprecated("renamed to buildLocalMapping", ReplaceWith("buildLocalMapping(body)"))
fun <In, Out> IMonoStep<In>.mapLocal2(body: ILocalMappingBuilder<In, Out>.(IMonoStep<In>) -> Unit): IMonoStep<Out> {
    return buildLocalMapping(body)
}

fun <In, Out> IMonoStep<In>.buildLocalMapping(body: ILocalMappingBuilder<In, Out>.(IMonoStep<In>) -> Unit): IMonoStep<Out> {
    val builder = LocalMappingBuilder<In, Out>()
    builder.apply {
        body(this@buildLocalMapping)
    }
    return builder.compileProcessingOutputStep()
}

fun <In, Out> IMonoStep<In>.mapLocal(body: (In) -> Out) = LocalMappingStep(body).connectAndDowncast(this)
fun <In, Out> IFluxStep<In>.mapLocal(body: (In) -> Out) = LocalMappingStep(body).connectAndDowncast(this)
fun <In, Out> IMonoStep<In>.executeLocal(body: (In) -> Out) = ExecuteLocalStep(body).connectAndDowncast(this)
fun <In, Out> IFluxStep<In>.executeLocal(body: (In) -> Out) = ExecuteLocalStep(body).connectAndDowncast(this)
