package org.modelix.model.mutable

import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.NodeReference
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData

fun ModelData.load(tree: IMutableModelTree) {
    tree.runWrite { t ->
        for (nodeData in root.children) {
            loadNode(nodeData, t.tree.getRootNodeId(), t, tree)
        }
    }
}

private fun ModelData.loadNode(
    nodeData: NodeData,
    parentId: INodeReference,
    t: IGenericMutableModelTree.WriteTransaction<INodeReference>,
    mutableTree: IMutableModelTree,
) {
    val conceptRef = nodeData.concept?.let { ConceptReference(it) } ?: NullConcept.getReference()

    val role = IChildLinkReference.fromString(nodeData.role)
    val nodeId = nodeData.id?.let { NodeReference(it) }
        ?: mutableTree.getIdGenerator().generate(parentId, role, conceptRef)
    t.mutate(
        MutationParameters.AddNew(
            parentId,
            role,
            -1,
            listOf(nodeId to conceptRef),
        ),
    )

    nodeData.properties.forEach {
        t.mutate(
            MutationParameters.Property(
                nodeId,
                IPropertyReference.fromString(it.key),
                it.value,
            ),
        )
    }
    nodeData.references.forEach {
        t.mutate(
            MutationParameters.Reference(
                nodeId,
                IReferenceLinkReference.fromString(it.key),
                NodeReference(it.value),
            ),
        )
    }

    nodeData.children.forEach {
        loadNode(it, nodeId, t, mutableTree)
    }
}
