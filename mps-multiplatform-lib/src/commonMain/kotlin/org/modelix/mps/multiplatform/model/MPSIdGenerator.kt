package org.modelix.mps.multiplatform.model

import org.modelix.model.TreeId
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INodeReference
import org.modelix.model.mutable.INodeIdGenerator

class MPSIdGenerator(val int64Generator: IIdGenerator, val treeId: TreeId) : INodeIdGenerator<INodeReference> {
    override fun generate(parentNode: INodeReference): INodeReference {
        val modelRef = MPSNodeReference.tryConvert(parentNode)?.modelReference
            ?: MPSModelReference.tryConvert(parentNode)
        require(modelRef != null) { "Cannot generate IDs for nodes in $parentNode" }

        // foreign ID
        val nodeId = "~${escapeRefChars(treeId.id)}-${int64Generator.generate().toULong().toString(16)}"

        return MPSNodeReference(modelRef, nodeId)
    }
}
