package org.modelix.mps.multiplatform.model

import org.modelix.model.TreeId
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INodeReference
import org.modelix.model.mutable.INodeIdGenerator

class MPSIdGenerator(val int64Generator: IIdGenerator, val treeId: TreeId) : INodeIdGenerator<INodeReference> {
    override fun generate(
        parentNode: INodeReference,
        role: IChildLinkReference,
        concept: ConceptReference,
    ): INodeReference {
        return when {
            BuiltinLanguages.MPSRepositoryConcepts.Language.getReference() == concept -> MPSModuleReference.random()
            BuiltinLanguages.MPSRepositoryConcepts.Solution.getReference() == concept -> MPSModuleReference.random()
            BuiltinLanguages.MPSRepositoryConcepts.Generator.getReference() == concept -> MPSModuleReference.random()
            BuiltinLanguages.MPSRepositoryConcepts.DevKit.getReference() == concept -> MPSModuleReference.random()
            BuiltinLanguages.MPSRepositoryConcepts.Model.getReference() == concept -> MPSModelReference.random()
            role.matches(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference()) -> MPSModuleReference.random()
            role.matches(BuiltinLanguages.MPSRepositoryConcepts.Module.models.toReference()) -> MPSModelReference.random()
            role.matches(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.toReference()) -> {
                val modelRef = MPSModelReference.tryConvert(parentNode)
                require(modelRef != null) { "Cannot generate IDs for nodes in $parentNode" }
                generateNodeId(modelRef)
            }
            else -> {
                val modelRef = MPSNodeReference.tryConvert(parentNode)?.modelReference
                require(modelRef != null) { "Cannot generate IDs for nodes in $parentNode" }
                generateNodeId(modelRef)
            }
        }
    }

    private fun generateNodeId(modelRef: MPSModelReference): INodeReference {
        // foreign ID
        val nodeId = "~${escapeRefChars(treeId.id)}-${int64Generator.generate().toULong().toString(16)}"
        return MPSNodeReference(modelRef, nodeId)
    }
}
