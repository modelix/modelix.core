package org.modelix.modelql.typed

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import org.modelix.metamodel.IConceptOfTypedNode
import org.modelix.metamodel.ITypedChildLink
import org.modelix.metamodel.ITypedChildListLink
import org.modelix.metamodel.ITypedConcept
import org.modelix.metamodel.ITypedMandatorySingleChildLink
import org.modelix.metamodel.ITypedNode
import org.modelix.metamodel.ITypedProperty
import org.modelix.metamodel.ITypedReferenceLink
import org.modelix.metamodel.ITypedSingleChildLink
import org.modelix.metamodel.typed
import org.modelix.metamodel.typedUnsafe
import org.modelix.metamodel.untyped
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.getAllSubConcepts
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IProducingStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.IStreamInstantiationContext
import org.modelix.modelql.core.IdentityStep
import org.modelix.modelql.core.MonoTransformingStep
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.StepStream
import org.modelix.modelql.core.asStepOutput
import org.modelix.modelql.core.asString
import org.modelix.modelql.core.emptyStringIfNull
import org.modelix.modelql.core.equalTo
import org.modelix.modelql.core.filterNotNull
import org.modelix.modelql.core.first
import org.modelix.modelql.core.firstOrNull
import org.modelix.modelql.core.flatMap
import org.modelix.modelql.core.inSet
import org.modelix.modelql.core.map
import org.modelix.modelql.core.mapIfNotNull
import org.modelix.modelql.core.nullMono
import org.modelix.modelql.core.orNull
import org.modelix.modelql.core.stepOutputSerializer
import org.modelix.modelql.core.toBoolean
import org.modelix.modelql.core.toInt
import org.modelix.modelql.core.upcast
import org.modelix.modelql.untyped.UntypedModelQL
import org.modelix.modelql.untyped.addNewChild
import org.modelix.modelql.untyped.children
import org.modelix.modelql.untyped.conceptReference
import org.modelix.modelql.untyped.descendants
import org.modelix.modelql.untyped.getUID
import org.modelix.modelql.untyped.nodeReference
import org.modelix.modelql.untyped.nodeReferenceAsString
import org.modelix.modelql.untyped.ofConcept
import org.modelix.modelql.untyped.property
import org.modelix.modelql.untyped.query
import org.modelix.modelql.untyped.reference
import org.modelix.modelql.untyped.remove
import org.modelix.modelql.untyped.setProperty
import org.modelix.modelql.untyped.setReference
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

object TypedModelQL {
    val serializersModule = SerializersModule {
        include(UntypedModelQL.serializersModule)
    }

    fun rawProperty(input: IMonoStep<ITypedNode>, link: ITypedProperty<*>): IMonoStep<String?> {
        return input.untyped().property(link.untyped().toReference())
    }
    fun rawProperty(input: IFluxStep<ITypedNode>, link: ITypedProperty<*>): IFluxStep<String?> {
        return input.untyped().property(link.untyped().toReference())
    }
    fun optionalStringProperty(input: IMonoStep<ITypedNode>, link: ITypedProperty<String>): IMonoStep<String?> {
        return rawProperty(input, link)
    }
    fun optionalStringProperty(input: IFluxStep<ITypedNode>, link: ITypedProperty<String>): IFluxStep<String?> {
        return rawProperty(input, link)
    }
    fun stringProperty(input: IMonoStep<ITypedNode>, link: ITypedProperty<String>): IMonoStep<String> {
        return optionalStringProperty(input, link).emptyStringIfNull()
    }
    fun stringProperty(input: IFluxStep<ITypedNode>, link: ITypedProperty<String>): IFluxStep<String> {
        return optionalStringProperty(input, link).emptyStringIfNull()
    }
    fun booleanProperty(input: IMonoStep<ITypedNode>, link: ITypedProperty<Boolean>): IMonoStep<Boolean> {
        return rawProperty(input, link).toBoolean()
    }
    fun booleanProperty(input: IFluxStep<ITypedNode>, link: ITypedProperty<Boolean>): IFluxStep<Boolean> {
        return rawProperty(input, link).toBoolean()
    }
    fun intProperty(input: IMonoStep<ITypedNode>, link: ITypedProperty<Int>): IMonoStep<Int> {
        return rawProperty(input, link).toInt()
    }
    fun intProperty(input: IFluxStep<ITypedNode>, link: ITypedProperty<Int>): IFluxStep<Int> {
        return rawProperty(input, link).toInt()
    }

    @JvmName("setStringProperty")
    fun <NodeT : ITypedNode, ValueT : String?> setProperty(input: IMonoStep<NodeT>, link: ITypedProperty<ValueT>, value: IMonoStep<ValueT>): IMonoStep<NodeT> {
        input.untyped().setProperty(link.untyped(), value)
        return input
    }

    @JvmName("setProperty")
    fun <NodeT : ITypedNode, ValueT : Any?> setProperty(input: IMonoStep<NodeT>, link: ITypedProperty<ValueT>, value: IMonoStep<ValueT>): IMonoStep<NodeT> {
        input.untyped().setProperty(link.untyped(), value.asString())
        return input
    }

    fun <ParentT : ITypedNode, ChildT : ITypedNode> children(input: IMonoStep<ParentT>, link: ITypedMandatorySingleChildLink<ChildT>): IMonoStep<ChildT> {
        return input.untyped().children(link.untyped().toReference()).typedUnsafe(link.getTypedChildConcept().getInstanceInterface()).first()
    }
    fun <ParentT : ITypedNode, ChildT : ITypedNode> children(input: IMonoStep<ParentT>, link: ITypedSingleChildLink<ChildT>): IMonoStep<ChildT?> {
        return input.untyped().children(link.untyped().toReference()).typedUnsafe(link.getTypedChildConcept().getInstanceInterface()).firstOrNull()
    }
    fun <ParentT : ITypedNode, ChildT : ITypedNode> children(input: IProducingStep<ParentT>, link: ITypedChildListLink<ChildT>): IFluxStep<ChildT> {
        return input.flatMap { it.untyped().children(link.untyped().toReference()).typedUnsafe(link.getTypedChildConcept().getInstanceInterface()) }
    }
    fun <ParentT : ITypedNode, ChildT : ITypedNode> children(input: IFluxStep<ParentT>, link: ITypedChildLink<ChildT>): IFluxStep<ChildT> {
        return input.untyped().flatMap { it.children(link.untyped().toReference()) }.typedUnsafe(link.getTypedChildConcept().getInstanceInterface())
    }

    fun <ParentT : ITypedNode, ChildT : ITypedNode, Out : ChildT> addNewChild(input: IMonoStep<ParentT>, link: ITypedChildListLink<ChildT>, index: Int = -1, concept: IConceptOfTypedNode<Out>): IMonoStep<Out> {
        val conceptRef = ConceptReference(concept.untyped().getUID())
        return input.untyped().addNewChild(link.untyped().toReference(), index, conceptRef).ofConcept(concept)
    }

    fun <ParentT : ITypedNode, ChildT : ITypedNode, Out : ChildT> setChild(input: IMonoStep<ParentT>, link: ITypedSingleChildLink<ChildT>, concept: IConceptOfTypedNode<Out>): IMonoStep<Out> {
        val conceptRef = ConceptReference(concept.untyped().getUID())
        input.untyped().children(link.untyped().toReference()).firstOrNull().mapIfNotNull { it.remove() }
        return input.untyped().addNewChild(link.untyped().toReference(), conceptRef).ofConcept(concept)
    }

    fun <SourceT : ITypedNode, TargetT : ITypedNode> reference(input: IMonoStep<SourceT>, link: ITypedReferenceLink<TargetT>): IMonoStep<TargetT> {
        return input.untyped().reference(link.untyped().toReference()).typedUnsafe<TargetT>(link.getTypedTargetConcept().getInstanceInterface())
    }
    fun <SourceT : ITypedNode, TargetT : ITypedNode> reference(input: IFluxStep<SourceT>, link: ITypedReferenceLink<TargetT>): IFluxStep<TargetT> {
        return input.map { reference(it, link) }
    }
    fun <SourceT : ITypedNode, TargetT : ITypedNode> referenceOrNull(input: IMonoStep<SourceT>, link: ITypedReferenceLink<TargetT>): IMonoStep<TargetT?> {
        return reference(input, link).orNull()
    }
    fun <SourceT : ITypedNode, TargetT : ITypedNode> referenceOrNull(input: IFluxStep<SourceT>, link: ITypedReferenceLink<TargetT>): IFluxStep<TargetT?> {
        return input.map { referenceOrNull(it, link) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <SourceT : ITypedNode, TargetT : ITypedNode> setReference(input: IMonoStep<SourceT>, link: ITypedReferenceLink<TargetT>, target: IMonoStep<TargetT?>?): IMonoStep<SourceT> {
        val targetOrNull = target?.untyped() ?: nullMono<String>() as IMonoStep<INode?> // cast is necessary since nullMono<INode> cannot be serialized
        input.untyped().setReference(link.untyped().toReference(), targetOrNull)
        return input
    }
}

/** Doesn't check the concept when executed remotely. Use .ofConcept() to convert a node in a type safe way. */
inline fun <reified Typed : ITypedNode> IMonoStep<INode>.typedUnsafe(): IMonoStep<Typed> = typedUnsafe(Typed::class)

/** Doesn't check the concept when executed remotely. Use .ofConcept() to convert a node in a type safe way. */
inline fun <reified Typed : ITypedNode> IFluxStep<INode>.typedUnsafe(): IFluxStep<Typed> = typedUnsafe(Typed::class)

/** Doesn't check the concept when executed remotely. Use .ofConcept() to convert a node in a type safe way. */
fun <Typed : ITypedNode> IMonoStep<INode>.typedUnsafe(nodeClass: KClass<out Typed>): IMonoStep<Typed> = TypedNodeStep(nodeClass).connectAndDowncast(this)

/** Doesn't check the concept when executed remotely. Use .ofConcept() to convert a node in a type safe way. */
fun <Typed : ITypedNode> IFluxStep<INode>.typedUnsafe(nodeClass: KClass<out Typed>): IFluxStep<Typed> = TypedNodeStep(nodeClass).connectAndDowncast(this)

fun IMonoStep<ITypedNode>.untyped(): IMonoStep<INode> = UntypedNodeStep().connectAndDowncast(this)
fun IFluxStep<ITypedNode>.untyped(): IFluxStep<INode> = UntypedNodeStep().connectAndDowncast(this)

@JvmName("untyped_nullable")
fun IMonoStep<ITypedNode?>.untyped(): IMonoStep<INode?> = mapIfNotNull { it.untyped() }

@JvmName("untyped_nullable")
fun IFluxStep<ITypedNode?>.untyped(): IFluxStep<INode?> = mapIfNotNull { it.untyped() }

class TypedNodeStep<Typed : ITypedNode>(val nodeClass: KClass<out Typed>) : MonoTransformingStep<INode, Typed>() {
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Typed>> {
        val inputSerializer = getProducer().getOutputSerializer(serializationContext).upcast()
        return TypedNodeSerializer(nodeClass, inputSerializer).stepOutputSerializer(this)
    }

    override fun createStream(input: StepStream<INode>, context: IStreamInstantiationContext): StepStream<Typed> {
        return input.map { it.value.typed(nodeClass).asStepOutput(this) }
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return IdentityStep.IdentityStepDescriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.typed()"
    }
}

class TypedNodeSerializer<Typed : ITypedNode>(val nodeClass: KClass<out Typed>, val untypedSerializer: KSerializer<IStepOutput<INode>>) : KSerializer<Typed> {
    override fun deserialize(decoder: Decoder): Typed {
        return untypedSerializer.deserialize(decoder).value.typed(nodeClass)
    }

    override val descriptor: SerialDescriptor = untypedSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Typed) {
        untypedSerializer.serialize(encoder, value.untyped().asStepOutput(null))
    }
}

class UntypedNodeSerializer(val typedSerializer: KSerializer<IStepOutput<ITypedNode>>) : KSerializer<INode> {
    override fun deserialize(decoder: Decoder): INode {
        return typedSerializer.deserialize(decoder).value.untyped()
    }

    override val descriptor: SerialDescriptor = typedSerializer.descriptor

    override fun serialize(encoder: Encoder, value: INode) {
        typedSerializer.serialize(encoder, value.typedUnsafe<ITypedNode>().asStepOutput(null))
    }
}

class UntypedNodeStep : MonoTransformingStep<ITypedNode, INode>() {
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<INode>> {
        return UntypedNodeSerializer(getProducer().getOutputSerializer(serializationContext).upcast()).stepOutputSerializer(this)
    }

    override fun createStream(input: StepStream<ITypedNode>, context: IStreamInstantiationContext): StepStream<INode> {
        return input.map { it.value.unwrap().asStepOutput(this) }
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return IdentityStep.IdentityStepDescriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}\n.untyped()"
    }
}

@JvmName("instanceOfExactly_untyped_untyped")
fun IMonoStep<INode?>.instanceOfExactly(concept: IConcept): IMonoStep<Boolean> {
    return conceptReference().mapIfNotNull { it.getUID() }.equalTo(concept.getUID())
}

@JvmName("instanceOfExactly_untyped_typed")
fun IMonoStep<INode?>.instanceOfExactly(concept: ITypedConcept): IMonoStep<Boolean> {
    return instanceOfExactly(concept.untyped())
}

@JvmName("instanceOfExactly_typed_untyped")
fun IMonoStep<ITypedNode>.instanceOfExactly(concept: IConcept): IMonoStep<Boolean> {
    return untyped().instanceOfExactly(concept)
}

@JvmName("instanceOfExactly_typed_typed")
fun IMonoStep<ITypedNode>.instanceOfExactly(concept: ITypedConcept): IMonoStep<Boolean> {
    return untyped().instanceOfExactly(concept)
}

@JvmName("instanceOf_untyped_untyped")
fun IMonoStep<INode?>.instanceOf(concept: IConcept): IMonoStep<Boolean> {
    val subconceptUIDs = concept.getAllSubConcepts(true).map { it.getReference().getUID() }.toSet()
    return conceptReference().getUID().inSet(subconceptUIDs)
}

@JvmName("instanceOf_untyped")
fun <Out : ITypedNode> IMonoStep<INode?>.instanceOf(concept: IConceptOfTypedNode<Out>): IMonoStep<Boolean> {
    return instanceOf(concept.untyped())
}

@JvmName("instanceOf")
fun <In : ITypedNode, Out : In> IMonoStep<In?>.instanceOf(concept: IConceptOfTypedNode<Out>): IMonoStep<Boolean> {
    return untyped().instanceOf(concept)
}

@JvmName("ofConcept_untyped")
fun <Out : ITypedNode> IFluxStep<INode?>.ofConcept(concept: IConceptOfTypedNode<Out>): IFluxStep<Out> {
    return ofConcept(concept.untyped()).typedUnsafe(concept.getInstanceInterface())
}

@JvmName("ofConcept")
fun <In : ITypedNode, Out : In> IFluxStep<In?>.ofConcept(concept: IConceptOfTypedNode<Out>): IFluxStep<Out> {
    return untyped().ofConcept(concept.untyped()).typedUnsafe(concept.getInstanceInterface())
}

@JvmName("ofConcept_untyped")
fun <Out : ITypedNode> IMonoStep<INode?>.ofConcept(concept: IConceptOfTypedNode<Out>): IMonoStep<Out> {
    return ofConcept(concept.untyped()).typedUnsafe(concept.getInstanceInterface())
}

@JvmName("ofConcept")
fun <In : ITypedNode, Out : In> IMonoStep<In?>.ofConcept(concept: IConceptOfTypedNode<Out>): IMonoStep<Out> {
    return filterNotNull().untyped().ofConcept(concept)
}

fun IMonoStep<ITypedNode>.conceptReference(): IMonoStep<ConceptReference?> = untyped().conceptReference()
fun IFluxStep<ITypedNode>.conceptReference(): IFluxStep<ConceptReference?> = untyped().conceptReference()

fun IMonoStep<ITypedNode>.descendants(includeSelf: Boolean = false): IFluxStep<ITypedNode> = untyped().descendants(includeSelf).typedUnsafe()
fun IFluxStep<ITypedNode>.descendants(includeSelf: Boolean = false): IFluxStep<ITypedNode> = untyped().descendants(includeSelf).typedUnsafe()

fun IMonoStep<ITypedNode>.nodeReferenceAsString(): IMonoStep<String> = untyped().nodeReferenceAsString()
fun IFluxStep<ITypedNode>.nodeReferenceAsString(): IFluxStep<String> = untyped().nodeReferenceAsString()
fun IMonoStep<ITypedNode>.nodeReference(): IMonoStep<INodeReference> = untyped().nodeReference()
fun IFluxStep<ITypedNode>.nodeReference(): IFluxStep<INodeReference> = untyped().nodeReference()

suspend inline fun <reified NodeT : ITypedNode, R> NodeT.query(noinline body: (IMonoStep<NodeT>) -> IMonoStep<R>): R {
    return query(NodeT::class, body)
}

suspend fun <NodeT : ITypedNode, R> NodeT.query(nodeClass: KClass<out NodeT>, body: (IMonoStep<NodeT>) -> IMonoStep<R>): R {
    return untyped().query { body(it.typedUnsafe(nodeClass)) }
}
