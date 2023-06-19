package org.modelix.modelql.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEmpty
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule

class IfEmptyStep<In : Out, Out>(val alternative: UnboundQuery<Unit, Out>) : TransformingStep<In, Out>(), IFluxStep<Out> {
    override fun createFlow(input: Flow<In>, context: IFlowInstantiationContext): Flow<Out> {
        val downCastedInput: Flow<Out> = input
        return downCastedInput.onEmpty {
            emitAll(alternative.applyQuery(emptyFlow<Unit>()))
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Out> {
        TODO("Not yet implemented")
    }

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("ifEmpty")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return NullIfEmpty<Any?>()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.ifEmpty { $alternative }"""
    }
}

fun <RemoteIn : RemoteOut, RemoteOut> IMonoStep<RemoteIn>.ifEmpty(alternative: () -> IMonoStep<RemoteOut>): IMonoStep<RemoteOut> {
    // TODO .first() is not correct. 0 elements should be allowed.
    return IfEmptyStep<RemoteIn, RemoteOut>(UnboundQuery.build { alternative() }).first()
}

fun <RemoteIn : RemoteOut, RemoteOut> IFluxStep<RemoteIn>.ifEmpty(alternative: () -> IMonoStep<RemoteOut>): IFluxStep<RemoteOut> {
    return IfEmptyStep<RemoteIn, RemoteOut>(UnboundQuery.build { alternative() })
}

fun <RemoteIn : RemoteOut, RemoteOut> IProducingStep<RemoteIn>.ifEmpty(alternative: () -> IFluxStep<RemoteOut>): IFluxStep<RemoteOut> {
    return IfEmptyStep<RemoteIn, RemoteOut>(UnboundQuery.build { alternative() })
}
