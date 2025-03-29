package org.modelix.model.operations

import org.modelix.model.api.IChildLinkReference

data class RoleInNode(val nodeId: Long, val role: IChildLinkReference) {
    constructor(nodeId: Long, role: String?) : this(nodeId, IChildLinkReference.fromLegacyApi(role))
    override fun toString() = "${nodeId.toString(16)}.$role"
}
