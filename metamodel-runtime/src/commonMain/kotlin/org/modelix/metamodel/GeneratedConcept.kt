package org.modelix.metamodel

import org.modelix.model.api.*
import kotlin.reflect.KClass

abstract class GeneratedConcept<InstanceT : ITypedNode, WrapperT : ITypedConcept>(
    private val name: String,
    private val is_abstract: Boolean
) : IConcept {
    abstract val _typed: WrapperT
    abstract val instanceClass: KClass<InstanceT>
    private val propertiesMap: MutableMap<String, GeneratedProperty<*>> = LinkedHashMap()
    private val childLinksMap: MutableMap<String, GeneratedChildLink<*, *>> = LinkedHashMap()
    private val referenceLinksMap: MutableMap<String, GeneratedReferenceLink<*, *>> = LinkedHashMap()

    abstract fun wrap(node: INode): InstanceT

    override fun isAbstract(): Boolean {
        return is_abstract
    }

    fun <ValueT> newProperty(name: String, serializer: IPropertyValueSerializer<ValueT>, optional: Boolean): GeneratedProperty<ValueT> {
        return GeneratedProperty(this, name, optional, serializer).also {
            propertiesMap[name] = it
        }
    }

    fun <ChildNodeT : ITypedNode, ChildConceptT : ITypedConcept> newSingleChildLink(
        name: String,
        isOptional: Boolean,
        targetConcept: IConcept,
        childNodeInterface: KClass<ChildNodeT>
    ): GeneratedSingleChildLink<ChildNodeT, ChildConceptT> {
        return GeneratedSingleChildLink<ChildNodeT, ChildConceptT>(
            this,
            name,
            isOptional,
            targetConcept,
            childNodeInterface
        ).also {
            childLinksMap[name] = it
        }
    }

    fun <ChildNodeT : ITypedNode, ChildConceptT : ITypedConcept> newChildListLink(
        name: String,
        isOptional: Boolean,
        targetConcept: IConcept,
        childNodeInterface: KClass<ChildNodeT>
    ): GeneratedChildListLink<ChildNodeT, ChildConceptT> {
        return GeneratedChildListLink<ChildNodeT, ChildConceptT>(
            this,
            name,
            isOptional,
            targetConcept,
            childNodeInterface
        ).also {
            childLinksMap[name] = it
        }
    }

    fun <TargetNodeT : ITypedNode, TargetConceptT : ITypedConcept> newReferenceLink(
        name: String,
        isOptional: Boolean,
        targetConcept: IConcept,
        targetNodeInterface: KClass<TargetNodeT>
    ): GeneratedReferenceLink<TargetNodeT, TargetConceptT> {
        return GeneratedReferenceLink<TargetNodeT, TargetConceptT>(
            this,
            name,
            isOptional,
            targetConcept,
            targetNodeInterface
        ).also {
            referenceLinksMap[name] = it
        }
    }

    override fun getChildLink(name: String): IChildLink {
        return getAllChildLinks().find { it.name == name }
            ?: throw IllegalArgumentException("Concept ${getLongName()} doesn't contain child link $name")
    }

    override fun getProperty(name: String): IProperty {
        return getAllProperties().find { it.name == name }
            ?: throw IllegalArgumentException("Concept ${getLongName()} doesn't contain property $name")
    }

    override fun getReferenceLink(name: String): IReferenceLink {
        return getAllReferenceLinks().find { it.name == name }
            ?: throw IllegalArgumentException("Concept ${getLongName()} doesn't contain reference link $name")
    }

    override fun getReference(): IConceptReference {
        return ConceptReference(getUID())
    }

    override fun getShortName(): String {
        return name
    }

    override fun getLongName(): String {
        return language!!.getName() + "." + getShortName()
    }

    override fun getOwnChildLinks(): List<IChildLink> {
        return childLinksMap.values.toList()
    }

    override fun getOwnReferenceLinks(): List<IReferenceLink> {
        return referenceLinksMap.values.toList()
    }

    override fun getUID(): String {
        return UID_PREFIX + getLongName()
    }

    override fun isExactly(concept: IConcept?): Boolean {
        return concept == this
    }

    override fun isSubConceptOf(superConcept: IConcept?): Boolean {
        if (superConcept == null) return false
        if (isExactly(superConcept)) return true

        for (c in getDirectSuperConcepts()) {
            if (c.isSubConceptOf(superConcept)) return true
        }

        return false
    }

    override fun getOwnProperties(): List<IProperty> {
        return propertiesMap.values.toList()
    }

    override fun getAllProperties(): List<IProperty> {
        return getAllConcepts().flatMap { it.getOwnProperties() }
    }

    override fun getAllChildLinks(): List<IChildLink> {
        return getAllConcepts().flatMap { it.getOwnChildLinks() }
    }

    override fun getAllReferenceLinks(): List<IReferenceLink> {
        return getAllConcepts().flatMap { it.getOwnReferenceLinks() }
    }

    companion object {
        const val UID_PREFIX = "gen:"
    }
}

class GeneratedProperty<ValueT>(
    private val owner: IConcept,
    override val name: String,
    override val isOptional: Boolean,
    private val serializer: IPropertyValueSerializer<ValueT>
) : ITypedProperty<ValueT>, IProperty {
    override fun getConcept(): IConcept = owner
    override fun getUID(): String = getConcept().getUID() + "." + name
    override fun untyped(): IProperty = this

    override fun serializeValue(value: ValueT): String? = serializer.serialize(value)

    override fun deserializeValue(serialized: String?): ValueT = serializer.deserialize(serialized)
}

abstract class GeneratedChildLink<ChildNodeT : ITypedNode, ChildConceptT : ITypedConcept>(
    private val owner: IConcept,
    override val name: String,
    override val isMultiple: Boolean,
    override val isOptional: Boolean,
    override val targetConcept: IConcept,
    private val childNodeInterface: KClass<ChildNodeT>
) : IChildLink, ITypedChildLink<ChildNodeT> {
    @Deprecated("use .targetConcept")
    override val childConcept: IConcept = targetConcept

    override fun getConcept(): IConcept = owner

    override fun getUID(): String = getConcept().getUID() + "." + name

    override fun untyped(): IChildLink {
        return this
    }

    override fun castChild(childNode: INode): ChildNodeT {
        return childNode.typed(childNodeInterface)
    }
}
fun IChildLink.typed() = this as? GeneratedChildLink<ITypedNode, ITypedConcept>

class GeneratedSingleChildLink<ChildNodeT : ITypedNode, ChildConceptT : ITypedConcept>(
    owner: IConcept,
    name: String,
    isOptional: Boolean,
    targetConcept: IConcept,
    childNodeInterface: KClass<ChildNodeT>
) : GeneratedChildLink<ChildNodeT, ChildConceptT>(owner, name, false, isOptional, targetConcept, childNodeInterface) {

}

class GeneratedChildListLink<ChildNodeT : ITypedNode, ChildConceptT : ITypedConcept>(
    owner: IConcept,
    name: String,
    isOptional: Boolean,
    targetConcept: IConcept,
    childNodeInterface: KClass<ChildNodeT>
) : GeneratedChildLink<ChildNodeT, ChildConceptT>(owner, name, true, isOptional, targetConcept, childNodeInterface) {

}

class GeneratedReferenceLink<TargetNodeT : ITypedNode, TargetConceptT : ITypedConcept>(
    private val owner: IConcept,
    override val name: String,
    override val isOptional: Boolean,
    override val targetConcept: IConcept,
    private val targetNodeInterface: KClass<TargetNodeT>
) : IReferenceLink, ITypedReferenceLink<TargetNodeT> {

    override fun getConcept(): IConcept = owner

    override fun getUID(): String = getConcept().getUID() + "." + name

    override fun untyped(): IReferenceLink = this

    override fun castTarget(target: INode): TargetNodeT {
        return target.typed(targetNodeInterface)
    }
}
fun IReferenceLink.typed() = this as? GeneratedReferenceLink<ITypedNode, ITypedConcept>

