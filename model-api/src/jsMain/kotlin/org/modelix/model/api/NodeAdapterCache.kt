package org.modelix.model.api

import TypedNode
import kotlinx.browser.window

@JsExport
@ExperimentalJsExport
object NodeAdapterCache {

    private val caches = (1..5).map { HashMap<INode, Any>() }.toMutableList()
    private var anyAccess = false

    fun <In : Any, Out : Any> getCachedWrapper(node: In, wrapperFunction: (node: In) -> Out): Out {
        if (!anyAccess) {
            anyAccess = true

            // angular replaces the setTimeout function to trigger an update of the UI,
            // which results in an endless update in this case
            val w: dynamic = window
            val setTimeout: ((dynamic, Int) -> Unit) = w.setTimeout.__zone_symbol__OriginalDelegate ?: w.setTimeout
            setTimeout({
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
        if (node is TypedNode) return toINode(node.node)

        throw IllegalArgumentException("Unsupported node type: $node")
    }
}
