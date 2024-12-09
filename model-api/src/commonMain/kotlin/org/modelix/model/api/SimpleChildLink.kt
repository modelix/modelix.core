package org.modelix.model.api

class SimpleChildLink(
    private val simpleName: String,
    override val isMultiple: Boolean,
    override val isOptional: Boolean,
    override val targetConcept: IConcept,
    private val uid: String? = null,
    override val isOrdered: Boolean = true,
) : IChildLink {
    var owner: SimpleConcept? = null
    override val childConcept: IConcept = targetConcept

    override fun getConcept(): IConcept = owner!!

    override fun getUID(): String {
        return uid
            ?: owner?.let { it.getUID() + "." + simpleName }
            ?: simpleName
    }

    override fun getSimpleName(): String = simpleName
    override fun toReference(): IChildLinkReference = IChildLinkReference.fromIdAndName(uid, simpleName)
}
