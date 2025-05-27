package org.modelix.model.api

open class SimpleConcept(
    private val conceptName: String,
    private val is_abstract: Boolean = false,
    directSuperConcepts: Iterable<IConcept> = emptyList(),
    private val uid: String? = null,
) : IConcept {
    override var language: ILanguage? = null
    val properties: MutableList<SimpleProperty> = ArrayList()
    val childLinks: MutableList<IChildLink> = ArrayList()
    val referenceLinks: MutableList<IReferenceLink> = ArrayList()
    private val superConcepts: List<IConcept> = directSuperConcepts.toList()

    override fun isAbstract(): Boolean = this.is_abstract

    override fun getUID(): String = uid ?: getLongName()

    override fun getReference(): ConceptReference {
        return ConceptReference(getUID())
    }

    fun addProperty(p: SimpleProperty): SimpleConcept {
        p.owner = this
        properties.add(p)
        return this
    }

    fun addChildLink(l: SimpleChildLink): SimpleConcept {
        l.owner = this
        childLinks.add(l)
        return this
    }

    fun addReferenceLink(l: SimpleReferenceLink): SimpleConcept {
        l.owner = this
        referenceLinks.add(l)
        return this
    }

    override fun getChildLink(name: String): IChildLink {
        // This usage of .name is correct
        return getAllChildLinks().find { it.name == name } ?: throw RuntimeException("child link $conceptName.$name not found")
    }

    override fun getLongName(): String {
        val l = language
        return if (l == null) getShortName() else "${l.getName()}.$conceptName"
    }

    override fun getProperty(name: String): IProperty {
        // This usage of .name is correct
        return getAllProperties().find { it.name == name } ?: throw RuntimeException("property $conceptName.$name not found")
    }

    override fun getReferenceLink(name: String): IReferenceLink {
        // This usage of .name is correct
        return getAllReferenceLinks().find { it.name == name } ?: throw RuntimeException("reference link $conceptName.$name not found")
    }

    override fun getShortName() = conceptName

    override fun isExactly(concept: IConcept?): Boolean {
        if (concept == null) return false
        return concept == this || getUID() == concept.getUID()
    }

    override fun isSubConceptOf(superConcept: IConcept?): Boolean {
        if (superConcept == null) return false
        if (isExactly(superConcept)) return true

        for (c in getDirectSuperConcepts()) {
            if (c.isSubConceptOf(superConcept)) return true
        }

        return false
    }

    override fun getDirectSuperConcepts(): List<IConcept> {
        return superConcepts
    }

    override fun getOwnProperties(): List<IProperty> {
        return properties
    }

    override fun getOwnChildLinks(): List<IChildLink> {
        return childLinks
    }

    override fun getOwnReferenceLinks(): List<IReferenceLink> {
        return referenceLinks
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
}
