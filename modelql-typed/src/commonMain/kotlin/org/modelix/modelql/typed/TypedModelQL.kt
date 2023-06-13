package org.modelix.modelql.typed

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.metamodel.*
import org.modelix.model.api.*
import org.modelix.modelql.core.*
import org.modelix.modelql.modelapi.*
import kotlin.jvm.JvmName
import kotlin.reflect.KClass

object TypedModelQL {
    val serializersModule = SerializersModule {
        include(UntypedModelQL.serializersModule)
    }

    fun rawProperty(input: IMonoStep<ITypedNode>, link: ITypedProperty<*>): IMonoStep<String?> {
        return input.untyped().property(link.untyped().key())
    }
    fun rawProperty(input: IFluxStep<ITypedNode>, link: ITypedProperty<*>): IFluxStep<String?> {
        return input.map { rawProperty(it, link) }
    }
    fun optionalStringProperty(input: IMonoStep<ITypedNode>, link: ITypedProperty<String>): IMonoStep<String?> {
        return rawProperty(input, link)
    }
    fun optionalStringProperty(input: IFluxStep<ITypedNode>, link: ITypedProperty<String>): IFluxStep<String?> {
        return input.map { optionalStringProperty(it, link) }
    }
    fun stringProperty(input: IMonoStep<ITypedNode>, link: ITypedProperty<String>): IMonoStep<String> {
        return optionalStringProperty(input, link).emptyStringIfNull()
    }
    fun stringProperty(input: IFluxStep<ITypedNode>, link: ITypedProperty<String>): IFluxStep<String> {
        return input.map { stringProperty(it, link) }
    }
    fun booleanProperty(input: IMonoStep<ITypedNode>, link: ITypedProperty<Boolean>): IMonoStep<Boolean> {
        return rawProperty(input, link).toBoolean()
    }
    fun booleanProperty(input: IFluxStep<ITypedNode>, link: ITypedProperty<Boolean>): IFluxStep<Boolean> {
        return input.map { booleanProperty(it, link) }
    }
    fun intProperty(input: IMonoStep<ITypedNode>, link: ITypedProperty<Int>): IMonoStep<Int> {
        return rawProperty(input, link).toInt()
    }
    fun intProperty(input: IFluxStep<ITypedNode>, link: ITypedProperty<Int>): IFluxStep<Int> {
        return input.map { intProperty(it, link) }
    }

    fun <ParentT : ITypedNode, ChildT : ITypedNode> children(input: IMonoStep<ParentT>, link: ITypedMandatorySingleChildLink<ChildT>): IMonoStep<ChildT> {
        return input.untyped().children(link.untyped().key()).typedUnsafe(link.getTypedChildConcept().getInstanceInterface()).first()
    }
    fun <ParentT : ITypedNode, ChildT : ITypedNode> children(input: IMonoStep<ParentT>, link: ITypedSingleChildLink<ChildT>): IMonoStep<ChildT?> {
        return input.untyped().children(link.untyped().key()).typedUnsafe(link.getTypedChildConcept().getInstanceInterface()).firstOrNull()
    }
    fun <ParentT : ITypedNode, ChildT : ITypedNode> children(input: IProducingStep<ParentT>, link: ITypedChildListLink<ChildT>): IFluxStep<ChildT> {
        return input.flatMap { it.untyped().children(link.untyped().key()).typedUnsafe(link.getTypedChildConcept().getInstanceInterface()) }
    }
    fun <ParentT : ITypedNode, ChildT : ITypedNode> children(input: IFluxStep<ParentT>, link: ITypedChildLink<ChildT>): IFluxStep<ChildT> {
        return input.untyped().flatMap { it.children(link.untyped().key()) }.typedUnsafe(link.getTypedChildConcept().getInstanceInterface())
    }

    fun <SourceT : ITypedNode, TargetT : ITypedNode> reference(input: IMonoStep<SourceT>, link: ITypedReferenceLink<TargetT>): IMonoStep<TargetT> {
        return input.untyped().reference(link.untyped().key()).typedUnsafe<TargetT>(link.getTypedTargetConcept().getInstanceInterface())
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
}

/** Doesn't check the concept when executed remotely. Use .ofConcept() to convert a node in a type safe way. */
inline fun <reified Typed : ITypedNode> IMonoStep<INode>.typedUnsafe(): IMonoStep<Typed> = typedUnsafe(Typed::class)
/** Doesn't check the concept when executed remotely. Use .ofConcept() to convert a node in a type safe way. */
inline fun <reified Typed : ITypedNode> IFluxStep<INode>.typedUnsafe(): IFluxStep<Typed> = typedUnsafe(Typed::class)
/** Doesn't check the concept when executed remotely. Use .ofConcept() to convert a node in a type safe way. */
fun <Typed : ITypedNode> IMonoStep<INode>.typedUnsafe(nodeClass: KClass<out Typed>): IMonoStep<Typed> = TypedNodeStep(nodeClass).also { connect(it) }
/** Doesn't check the concept when executed remotely. Use .ofConcept() to convert a node in a type safe way. */
fun <Typed : ITypedNode> IFluxStep<INode>.typedUnsafe(nodeClass: KClass<out Typed>): IFluxStep<Typed> = map { it.typedUnsafe(nodeClass) }

fun IMonoStep<ITypedNode>.untyped(): IMonoStep<INode> = UntypedNodeStep().also { connect(it) }
fun IFluxStep<ITypedNode>.untyped(): IFluxStep<INode> = map { it.untyped() }

class TypedNodeStep<Typed : ITypedNode>(val nodeClass: KClass<out Typed>) : MonoTransformingStep<INode, Typed>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Typed> {
        return TypedNodeSerializer(nodeClass, serializersModule.serializer<INode>())
    }

    override fun transform(element: INode): Sequence<Typed> {
        return sequenceOf(element.typed(nodeClass))
    }

    override fun createDescriptor(): StepDescriptor {
        return IdentityStep.IdentityStepDescriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}.typed()"
    }
}

class TypedNodeSerializer<Typed : ITypedNode>(val nodeClass: KClass<out Typed>, val untypedSerializer: KSerializer<INode>) : KSerializer<Typed> {
    override fun deserialize(decoder: Decoder): Typed {
        return decoder.decodeSerializableValue(untypedSerializer).typed(nodeClass)
    }

    override val descriptor: SerialDescriptor = untypedSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Typed) {
        encoder.encodeSerializableValue(untypedSerializer, value.untyped())
    }
}

class UntypedNodeStep : MonoTransformingStep<ITypedNode, INode>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<INode> {
        return serializersModule.serializer<INode>()
    }

    override fun transform(element: ITypedNode): Sequence<INode> {
        return sequenceOf(element.unwrap())
    }

    override fun createDescriptor(): StepDescriptor {
        return IdentityStep.IdentityStepDescriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}.untyped()"
    }
}

@JvmName("instanceOf_untyped")
fun <Out : ITypedNode> IMonoStep<INode?>.instanceOf(concept: IConceptOfTypedNode<Out>): IMonoStep<Boolean> {
    val subconceptUIDs = concept.untyped().getAllSubConcepts(true).map { it.getReference().getUID() }.toSet()
    return conceptReference().filterNotNull().getUID().inSet(subconceptUIDs)
}

@JvmName("instanceOf")
fun <In : ITypedNode, Out : In> IMonoStep<In?>.instanceOf(concept: IConceptOfTypedNode<Out>): IMonoStep<Boolean> {
    return filterNotNull().untyped().instanceOf(concept)
}

@JvmName("ofConcept_untyped")
fun <Out : ITypedNode> IFluxStep<INode?>.ofConcept(concept: IConceptOfTypedNode<Out>): IFluxStep<Out> {
    return filterNotNull().filter { it.instanceOf(concept) }.typedUnsafe(concept.getInstanceInterface())
}
@JvmName("ofConcept")
fun <In : ITypedNode, Out : In> IFluxStep<In?>.ofConcept(concept: IConceptOfTypedNode<Out>): IFluxStep<Out> {
    return filterNotNull().filter { it.instanceOf(concept) } as IFluxStep<Out>
}

@JvmName("ofConcept_untyped")
fun <Out : ITypedNode> IMonoStep<INode?>.ofConcept(concept: IConceptOfTypedNode<Out>): IMonoStep<Out> {
    return filterNotNull().filter { it.instanceOf(concept) }.typedUnsafe(concept.getInstanceInterface())
}
@JvmName("ofConcept")
fun <In : ITypedNode, Out : In> IMonoStep<In?>.ofConcept(concept: IConceptOfTypedNode<Out>): IMonoStep<Out> {
    return filterNotNull().untyped().ofConcept(concept)
}

fun IMonoStep<ITypedNode>.conceptReference(): IMonoStep<ConceptReference?> = untyped().conceptReference()
fun IFluxStep<ITypedNode>.conceptReference(): IFluxStep<ConceptReference?> = map { it.conceptReference() }

fun IMonoStep<ITypedNode>.descendants(includeSelf: Boolean = false): IFluxStep<ITypedNode> = untyped().descendants(includeSelf).typedUnsafe()
fun IFluxStep<ITypedNode>.descendants(includeSelf: Boolean = false): IFluxStep<ITypedNode> = untyped().descendants(includeSelf).typedUnsafe()

fun IMonoStep<ITypedNode>.nodeReferenceAsString(): IMonoStep<String> = untyped().nodeReferenceAsString()
fun IFluxStep<ITypedNode>.nodeReferenceAsString(): IFluxStep<String> = untyped().nodeReferenceAsString()
fun IMonoStep<ITypedNode>.nodeReference(): IMonoStep<INodeReference> = untyped().nodeReference()
fun IFluxStep<ITypedNode>.nodeReference(): IFluxStep<INodeReference> = untyped().nodeReference()