package org.modelix.model.api

import kotlin.jvm.JvmOverloads

data class SimpleProperty
@JvmOverloads constructor(
    private val simpleName: String,
    override val isOptional: Boolean = true,
    private val uid: String? = null,
) : IProperty {
    var owner: SimpleConcept? = null

    override fun getConcept(): IConcept = owner!!

    override fun getUID(): String {
        return uid
            ?: owner?.let { it.getUID() + "." + simpleName }
            ?: simpleName
    }

    override fun getSimpleName(): String = simpleName
    override fun toReference(): IPropertyReference = IPropertyReference.fromIdAndName(uid, simpleName)
}
