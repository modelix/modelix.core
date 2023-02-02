package org.modelix.metamodel

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ILanguage
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.area.IArea

abstract class EmptyConcept : IConcept {
    override fun isAbstract(): Boolean = true

    override fun isSubConceptOf(superConcept: IConcept?): Boolean = superConcept == this

    override fun getDirectSuperConcepts(): List<IConcept> = emptyList()

    override fun isExactly(concept: IConcept?): Boolean = concept == this

    override fun getOwnProperties(): List<IProperty> = emptyList()

    override fun getOwnChildLinks(): List<IChildLink> = emptyList()

    override fun getOwnReferenceLinks(): List<IReferenceLink> = emptyList()

    override fun getAllProperties(): List<IProperty> = emptyList()

    override fun getAllChildLinks(): List<IChildLink> = emptyList()

    override fun getAllReferenceLinks(): List<IReferenceLink> = emptyList()

    override fun getProperty(name: String): IProperty {
        throw IllegalArgumentException("Cannot get property '$name'. No concept information available for '${getUID()}'.")
    }

    override fun getChildLink(name: String): IChildLink {
        throw IllegalArgumentException("Cannot get link '$name'. No concept information available for '${getUID()}'.")
    }

    override fun getReferenceLink(name: String): IReferenceLink {
        throw IllegalArgumentException("Cannot get link '$name'. No concept information available for '${getUID()}'.")
    }
}

object NullConcept : EmptyConcept(), IConceptReference {
    override fun getReference(): IConceptReference = this

    override val language: ILanguage?
        get() = null

    override fun getUID(): String = "null"

    override fun getShortName(): String = "null"

    override fun getLongName(): String = getShortName()

    override fun resolve(area: IArea?): IConcept? {
        return this
    }

    override fun serialize(): String = "null"
}

data class UnknownConcept(private val ref: IConceptReference) : EmptyConcept() {
    override fun getReference(): IConceptReference {
        return ref
    }

    override val language: ILanguage?
        get() = null

    override fun getUID(): String = ref.getUID()

    override fun getShortName(): String = "Unknown[${ref.getUID()}]"

    override fun getLongName(): String = getShortName()
}

data class UnknownTypedConcept(private val ref: IConceptReference?) : ITypedConcept {
    override fun untyped(): IConcept {
        return ref?.let { UnknownConcept(it) } ?: NullConcept
    }
}

data class UnknownConceptInstance(val node: INode) : ITypedNode {
    override val _concept: ITypedConcept
        get() = UnknownTypedConcept(node.getConceptReference())

    override fun unwrap(): INode = node
}