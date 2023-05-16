package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.modules.SerializersModule

abstract class CollectorStep<RemoteE, RemoteCollectionT : Collection<RemoteE>>()
    : IConsumingStep<RemoteE>,
      ProducingStep<RemoteCollectionT>(),
      ITerminalStep<RemoteCollectionT> {

    private var producer: IProducingStep<RemoteE>? = null

    override fun addProducer(producer: IProducingStep<RemoteE>) {
        if (this.producer != null) throw IllegalStateException("Only one input supported")
        this.producer = producer
    }

    override fun getProducers(): List<IProducingStep<*>> {
        return listOfNotNull(producer)
    }

    protected var collection: MutableCollection<RemoteE> = createCollection()

    override fun onNext(element: RemoteE, source: IProducingStep<RemoteE>) {
        collection.add(element)
    }

    override fun onComplete(source: IProducingStep<RemoteE>) {
        forwardToConsumers(collection as RemoteCollectionT)
        completeConsumers()
    }

    override fun getResult(): RemoteCollectionT {
        return collection as RemoteCollectionT
    }

    override fun reset() {
        collection = createCollection()
    }

    protected abstract fun createCollection(): MutableCollection<RemoteE>
}

class ListCollectorStep<RemoteE> : CollectorStep<RemoteE, List<RemoteE>>() {
    override fun createCollection() = ArrayList<RemoteE>()

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("toList")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return ListCollectorStep<Any?>()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<List<RemoteE>> {
        val element = getProducers().first().getOutputSerializer(serializersModule) as KSerializer<RemoteE>
        return ListSerializer<RemoteE>(element)
    }

    override fun toString(): String {
        return "${getProducers().single()}.toList()"
    }
}

class SetCollectorStep<RemoteE> : CollectorStep<RemoteE, Set<RemoteE>>() {
    override fun createCollection() = LinkedHashSet<RemoteE>()

    override fun createDescriptor() = Descriptor()

    @Serializable
    @SerialName("toSet")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(): IStep {
            return SetCollectorStep<Any?>()
        }
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Set<RemoteE>> {
        val element = getProducers().single().getOutputSerializer(serializersModule) as KSerializer<RemoteE>
        return SetSerializer<RemoteE>(element)
    }

    override fun toString(): String {
        return "${getProducers().single()}.toSet()"
    }
}

fun <RemoteOut> IFluxStep<RemoteOut>.toList(): ITerminalStep<List<RemoteOut>> = ListCollectorStep<RemoteOut>().also { connect(it) }
fun <RemoteOut> IMonoStep<RemoteOut>.toSingletonList(): ITerminalStep<List<RemoteOut>> = ListCollectorStep<RemoteOut>().also { connect(it) }
fun <RemoteOut> IFluxStep<RemoteOut>.toSet(): ITerminalStep<Set<RemoteOut>> = SetCollectorStep<RemoteOut>().also { connect(it) }
fun <RemoteOut> IMonoStep<RemoteOut>.toSingletonSet(): ITerminalStep<Set<RemoteOut>> = SetCollectorStep<RemoteOut>().also { connect(it) }
