package org.modelix.mps.multiplatform.model

import org.modelix.model.api.INodeReference

object MPSRepositoryReference : INodeReference() {
    const val PREFIX = "mps-repository"

    override fun serialize(): String {
        return "$PREFIX:repository"
    }
}
