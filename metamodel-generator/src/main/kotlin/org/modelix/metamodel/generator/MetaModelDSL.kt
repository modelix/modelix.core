package org.modelix.metamodel.generator

fun newLanguage(name: String, body: LanguageBuilder.()->Unit): LanguageData {
    return LanguageBuilder(name).apply(body).build()
}

class LanguageBuilder(val name: String) {
    private val concepts = ArrayList<ConceptData>()
    fun build(): LanguageData {
        return LanguageData(
            name = name,
            concepts = concepts
        )
    }

    fun concept(name: String, body: ConceptBuilder.()->Unit = {}) {
        concepts.add(ConceptBuilder(name, this).apply(body).build())
    }
}

class ConceptBuilder(val conceptName: String, val languageBuilder: LanguageBuilder) {
    private var abstract: Boolean = false
    private val properties: MutableList<PropertyData> = ArrayList()
    private val children: MutableList<ChildLinkData> = ArrayList()
    private val references: MutableList<ReferenceLinkData> = ArrayList()
    private val extends: MutableList<String> = ArrayList()

    fun abstract(value: Boolean = true) {
        abstract = value
    }

    fun property(name: String) {
        properties.add(PropertyData(name))
    }

    fun reference(name: String, type: String, optional: Boolean = false) {
        references.add(ReferenceLinkData(name, type, optional))
    }

    fun optionalReference(name: String, type: String) {
        reference(name, type, true)
    }

    fun child(name: String, type: String, optional: Boolean, multiple: Boolean) {
        children.add(ChildLinkData(name = name, type = type, multiple = multiple, optional = optional))
    }

    fun child0n(name: String, type: String) = child(name = name, type = type, optional = true, multiple = true)
    fun child1n(name: String, type: String) = child(name = name, type = type, optional = false, multiple = true)
    fun child0(name: String, type: String) = child(name = name, type = type, optional = true, multiple = false)
    fun child1(name: String, type: String) = child(name = name, type = type, optional = false, multiple = false)

    fun extends(type: String) {
        extends.add(type)
    }

    fun concept(subConceptName: String, body: ConceptBuilder.()->Unit = {}) {
        val parentBuilder = this
        languageBuilder.concept(subConceptName) {
            extends(parentBuilder.conceptName)
            body()
        }
    }

    fun build(): ConceptData {
        return ConceptData(
            name = conceptName,
            abstract = abstract,
            properties = properties,
            children = children,
            references = references,
            extends = extends
        )
    }
}
