package org.modelix.model

import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectHash
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.lazy.VersionBuilder
import kotlin.jvm.JvmInline

interface IVersion {
    fun getContentHash(): String

    @Deprecated("Use getModelTree()")
    fun getTree(): ITree

    fun getTrees(): Map<TreeType, ITree>

    fun getModelTree(): IGenericModelTree<INodeReference>

    fun getAttributes(): Map<String, String>

    fun asObject(): Object<*>

    fun getObjectHash(): ObjectHash = asObject().getHash()

    companion object {
        fun builder(): VersionBuilder = VersionBuilder()
    }
}

@JvmInline
value class TreeType(val name: String) {
    init {
        for (c in name) {
            if (!('a' <= c && c <= 'z' || 'A' <= c && c <= 'Z' || '0' <= c && c <= '9')) {
                throw IllegalArgumentException("Name contains illegal characters: $name")
            }
        }
    }

    override fun toString(): String = name.ifEmpty { "<main>" }

    companion object {
        val MAIN = TreeType("")
        val META = TreeType("meta")
    }
}
