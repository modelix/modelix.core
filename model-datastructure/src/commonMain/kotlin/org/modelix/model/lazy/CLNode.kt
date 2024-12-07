package org.modelix.model.lazy

import org.modelix.model.api.ITree
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPNodeRef
import kotlin.jvm.JvmStatic

@Deprecated("use CPNode")
class CLNode(private val tree: CLTree, private val data: CPNode) {
    constructor(
        tree: CLTree,
        id: Long,
        concept: String?,
        parentId: Long,
        roleInParent: String?,
        childrenIds: LongArray,
        propertyRoles: Array<String>,
        propertyValues: Array<String>,
        referenceRoles: Array<String>,
        referenceTargets: Array<CPNodeRef>,
    ) :
        this(
            tree,
            CPNode.create(
                id,
                concept,
                parentId,
                roleInParent,
                childrenIds,
                propertyRoles,
                propertyValues,
                referenceRoles,
                referenceTargets,
            ),
        ) {}

    val id: Long
        get() = data.id

    fun getTree(): ITree {
        return tree
    }

    val parentId: Long
        get() = data.parentId

    val roleInParent: String?
        get() = data.roleInParent

    val ref: CLNodeRef
        get() = CLNodeRef(id)

    fun getData(): CPNode {
        return data
    }

    val concept: String?
        get() = getData().concept

    companion object {
        @JvmStatic
        fun create(tree: CLTree, data: CPNode?): CLNode? {
            return data?.let { CLNode(tree, data) }
        }
    }
}
