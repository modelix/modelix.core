package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.SNodeId
import jetbrains.mps.smodel.SNodePointer
import jetbrains.mps.util.StringUtil
import org.modelix.model.TreeId
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INodeReference
import org.modelix.model.mutable.INodeIdGenerator

class MPSIdGenerator(val int64Generator: IIdGenerator, val treeId: TreeId) : INodeIdGenerator<INodeReference> {
    override fun generate(parentNode: INodeReference): INodeReference {
        val modelRef = MPSNodeReference.tryConvert(parentNode)?.ref?.modelReference
            ?: MPSModelReference.tryConvert(parentNode)?.modelReference
        require(modelRef != null) { "Cannot generate IDs for nodes in $parentNode" }
        val snodeId = SNodeId.Foreign("~${StringUtil.escapeRefChars(treeId.id)}-${int64Generator.generate().toULong().toString(16)}")
        return MPSNodeReference(SNodePointer(modelRef, snodeId))
    }
}
