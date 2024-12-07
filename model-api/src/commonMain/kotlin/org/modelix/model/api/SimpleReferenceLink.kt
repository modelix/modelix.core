package org.modelix.model.api

import kotlin.jvm.JvmOverloads

class SimpleReferenceLink
@JvmOverloads constructor(
    private val simpleName: String,
    override val isOptional: Boolean,
    override var targetConcept: IConcept,
    private val uid: String? = null,
) : IReferenceLink {
    var owner: SimpleConcept? = null

    override fun getConcept(): IConcept = owner!!

    override fun getUID(): String {
        return uid
            ?: owner?.let { it.getUID() + "." + simpleName }
            ?: simpleName
    }

    override fun getSimpleName(): String = simpleName
    override fun toReference(): IReferenceLinkReference = IReferenceLinkReference.fromIdAndName(uid, simpleName)
}
