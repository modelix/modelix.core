package org.modelix.metamodel

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ILanguage
import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.meta.EmptyConcept
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.api.upcast
import kotlin.reflect.KClass

data class UnknownConcept(private val ref: IConceptReference) : EmptyConcept() {
    override fun getReference(): ConceptReference {
        return ref.upcast()
    }

    override val language: ILanguage?
        get() = null

    override fun getUID(): String = ref.getUID()

    override fun getShortName(): String = "Unknown[${ref.getUID()}]"

    override fun getLongName(): String = getShortName()
}

data class UnknownTypedConcept(private val ref: IConcept?) : IConceptOfTypedNode<UnknownConceptInstance> {
    override fun untyped(): IConcept {
        return ref ?: NullConcept
    }

    override fun getInstanceInterface(): KClass<out UnknownConceptInstance> {
        return UnknownConceptInstance::class
    }
}

data class UnknownConceptInstance(val node: INode) : ITypedNode {
    override val _concept: ITypedConcept
        get() = UnknownTypedConcept(node.concept)

    override fun unwrap(): INode = node
}

abstract class UnknownTypedChildLink : ITypedChildLink<ITypedNode> {

    override fun castChild(childNode: INode): ITypedNode {
        return childNode.typed()
    }

    override fun getTypedChildConcept(): IConceptOfTypedNode<ITypedNode> {
        return untyped().targetConcept.typed() as IConceptOfTypedNode<ITypedNode>
    }
}

data class UnknownTypedSingleChildLink(private val link: IChildLink) : UnknownTypedChildLink(), ITypedSingleChildLink<ITypedNode> {
    override fun untyped(): IChildLink = link
}

data class UnknownTypedChildLinkList(private val link: IChildLink) : UnknownTypedChildLink(), ITypedChildListLink<ITypedNode> {
    override fun untyped(): IChildLink = link
}

data class UnknownTypedReferenceLink(private val link: IReferenceLink) : ITypedReferenceLink<ITypedNode> {
    override fun untyped(): IReferenceLink {
        return link
    }

    override fun castTarget(target: INode): ITypedNode {
        return target.typed()
    }

    override fun getTypedTargetConcept(): IConceptOfTypedNode<ITypedNode> {
        return untyped().targetConcept.typed() as IConceptOfTypedNode<ITypedNode>
    }
}
