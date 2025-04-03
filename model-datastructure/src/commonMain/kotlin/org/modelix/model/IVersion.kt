package org.modelix.model

import org.modelix.datastructures.model.IModelTree
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import kotlin.jvm.JvmInline

interface IVersion {
    fun getContentHash(): String

    @Deprecated("Use getModelTree()")
    fun getTree(): ITree

    fun getTrees(): Map<TreeType, ITree>

    fun getModelTree(): IModelTree<INodeReference>
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
