package org.modelix.model.api

import ITypedNode
import LanguageRegistry
import TypedNode
import kotlinx.browser.window

@JsExport
@ExperimentalJsExport
object NodeAdapterCache {

    private val caches = (1..5).map { HashMap<INode, Any>() }.toMutableList()
    private var anyAccess = false

    fun <In : Any, Out : Any> getCachedWrapper(node: In, wrapperFunction: (node: In)->Out): Out {
        if (!anyAccess) {
            anyAccess = true
            window.setTimeout({
                // This is expected to be executed after the update cycle of angular is finished.
                // If a node wrapper wasn't used after 5 update cycles, it gets evicted.
                anyAccess = false
                if (caches[0].size > 100) {
                    caches.add(0, HashMap())
                    caches.removeLast()
                }
            }, 0)
        }

        val key = toINode(node)
        var wrapper = caches[0][key]
        if (wrapper == null) {
            wrapper = caches.asSequence().drop(1).mapNotNull { it.remove(key) }.firstOrNull()
            if (wrapper == null) {
                wrapper = wrapperFunction(node)
            }
            caches[0][key] = wrapper
        }
        return wrapper as Out
    }

    private fun toINode(node: Any): INode {
        if (node is INode) return node
        if (node is NodeAdapterJS) return node.node

        // if (node is TypedNode) return toINode(node.node)

        // Workaround, because the line above fails with 'TypedNode is not defined'.
        // The import for '@modelix/ts-model-api' seems to be missing in the generated JS.
        val unwrapped = node.asDynamic().node
        if (unwrapped != null) return toINode(unwrapped)

        throw IllegalArgumentException("Unsupported node type: $node")
    }
}