package org.modelix.model.operations

import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.LocalPNodeReference

data class RoleInNode(val nodeId: INodeReference, val role: IChildLinkReference) {
    constructor(nodeId: INodeReference, role: String?) : this(nodeId, IChildLinkReference.fromLegacyApi(role))
    constructor(nodeId: Long, role: String?) : this(LocalPNodeReference(nodeId), IChildLinkReference.fromLegacyApi(role))
    override fun toString() = "$nodeId.$role"
}
