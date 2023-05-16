package org.modelix.modelql.typed

import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.metamodel.*
import org.modelix.model.api.*
import org.modelix.modelql.core.*
import org.modelix.modelql.modelapi.*
import kotlin.jvm.JvmName

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
        return input.untyped().children(link.untyped().key()).typed<ChildT>().first()
    }
    fun <ParentT : ITypedNode, ChildT : ITypedNode> children(input: IMonoStep<ParentT>, link: ITypedSingleChildLink<ChildT>): IMonoStep<ChildT?> {
        return input.untyped().children(link.untyped().key()).typed<ChildT>().firstOrNull()
    }
    fun <ParentT : ITypedNode, ChildT : ITypedNode> children(input: IProducingStep<ParentT>, link: ITypedChildListLink<ChildT>): IFluxStep<ChildT> {
        return input.flatMap { it.untyped().children(link.untyped().key()).typed<ChildT>() }
    }
    fun <ParentT : ITypedNode, ChildT : ITypedNode> children(input: IFluxStep<ParentT>, link: ITypedChildLink<ChildT>): IFluxStep<ChildT> {
        return input.untyped().flatMap { it.children(link.untyped().key()) }.typed<ChildT>()
    }

    fun <SourceT : ITypedNode, TargetT : ITypedNode> reference(input: IMonoStep<SourceT>, link: ITypedReferenceLink<TargetT>): IMonoStep<TargetT> {
        return input.untyped().reference(link.untyped().key()).typed<TargetT>()
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

fun <Typed : ITypedNode> IMonoStep<INode>.typed(): IMonoStep<Typed> = TypedNodeStep<Typed>().also { connect(it) }
fun <Typed : ITypedNode> IFluxStep<INode>.typed(): IFluxStep<Typed> = map { it.typed<Typed>() }
fun IMonoStep<ITypedNode>.untyped(): IMonoStep<INode> = UntypedNodeStep().also { connect(it) }
fun IFluxStep<ITypedNode>.untyped(): IFluxStep<INode> = map { it.untyped() }

class TypedNodeStep<Typed : ITypedNode> : MonoTransformingStep<INode, Typed>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<Typed> {
        throw UnsupportedOperationException("use .untyped() before returning the result")
    }

    override fun transform(element: INode): Sequence<Typed> {
        return sequenceOf(element.typedUnsafe())
    }

    override fun createDescriptor(): StepDescriptor {
        return IdentityStep.IdentityStepDescriptor()
    }

    override fun toString(): String {
        return "${getProducers().single()}.typed()"
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
    return filterNotNull().filter { it.instanceOf(concept) }.typed()
}
@JvmName("ofConcept")
fun <In : ITypedNode, Out : In> IFluxStep<In?>.ofConcept(concept: IConceptOfTypedNode<Out>): IFluxStep<Out> {
    return filterNotNull().filter { it.instanceOf(concept) } as IFluxStep<Out>
}

@JvmName("ofConcept_untyped")
fun <Out : ITypedNode> IMonoStep<INode?>.ofConcept(concept: IConceptOfTypedNode<Out>): IMonoStep<Out> {
    return filterNotNull().filter { it.instanceOf(concept) }.typed()
}
@JvmName("ofConcept")
fun <In : ITypedNode, Out : In> IMonoStep<In?>.ofConcept(concept: IConceptOfTypedNode<Out>): IMonoStep<Out> {
    return filterNotNull().untyped().ofConcept(concept)
}

fun IMonoStep<ITypedNode>.conceptReference(): IMonoStep<ConceptReference?> = untyped().conceptReference()
fun IFluxStep<ITypedNode>.conceptReference(): IFluxStep<ConceptReference?> = map { it.conceptReference() }

fun IMonoStep<ITypedNode>.descendants(includeSelf: Boolean = false): IFluxStep<ITypedNode> = untyped().descendants(includeSelf).typed()
fun IFluxStep<ITypedNode>.descendants(includeSelf: Boolean = false): IFluxStep<ITypedNode> = untyped().descendants(includeSelf).typed()

fun IMonoStep<ITypedNode>.nodeReferenceAsString(): IMonoStep<String> = untyped().nodeReferenceAsString()
fun IFluxStep<ITypedNode>.nodeReferenceAsString(): IFluxStep<String> = untyped().nodeReferenceAsString()
fun IMonoStep<ITypedNode>.nodeReference(): IMonoStep<INodeReference> = untyped().nodeReference()
fun IFluxStep<ITypedNode>.nodeReference(): IFluxStep<INodeReference> = untyped().nodeReference()