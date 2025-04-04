package org.modelix.datastructures.model

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.ITree
import org.modelix.model.api.NullChildLinkReference
import org.modelix.model.api.async.IAsyncMutableTree
import org.modelix.model.api.async.IAsyncTree
import org.modelix.model.api.async.TreeChangeEvent
import org.modelix.model.async.AsyncAsSynchronousTree
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider
import org.modelix.streams.ifEmpty

class ModelTreeAsLegacyAsyncTree(val tree: IGenericModelTree<Long>) : IAsyncMutableTree, IStreamExecutorProvider by tree {
    override fun asObject(): Any = tree.asObject()

    private fun IStream.One<IGenericModelTree<Long>>.wrap() = map { ModelTreeAsLegacyAsyncTree(it) }

    override fun asSynchronousTree(): ITree {
        return AsyncAsSynchronousTree(this)
    }

    override fun getChanges(oldVersion: IAsyncTree, changesOnly: Boolean): IStream.Many<TreeChangeEvent> {
        return tree.getChanges(oldVersion.asModelTree(), changesOnly).map { it.toLegacy() }
    }

    override fun getConceptReference(nodeId: Long): IStream.One<ConceptReference> {
        return tree.getConceptReference(nodeId)
    }

    override fun containsNode(nodeId: Long): IStream.One<Boolean> {
        return tree.containsNode(nodeId)
    }

    override fun getParent(nodeId: Long): IStream.ZeroOrOne<Long> {
        return tree.getParent(nodeId)
    }

    override fun getRole(nodeId: Long): IStream.One<IChildLinkReference> {
        return tree.getRoleInParent(nodeId).ifEmpty { NullChildLinkReference }
    }

    override fun getChildren(parentId: Long, role: IChildLinkReference): IStream.Many<Long> {
        return tree.getChildren(parentId, role)
    }

    override fun getChildRoles(sourceId: Long): IStream.Many<IChildLinkReference> {
        return tree.getChildRoles(sourceId)
    }

    override fun getAllChildren(parentId: Long): IStream.Many<Long> {
        return tree.getChildren(parentId)
    }

    override fun getPropertyValue(nodeId: Long, role: IPropertyReference): IStream.ZeroOrOne<String> {
        return tree.getProperty(nodeId, role)
    }

    override fun getPropertyRoles(sourceId: Long): IStream.Many<IPropertyReference> {
        return tree.getPropertyRoles(sourceId)
    }

    override fun getAllPropertyValues(sourceId: Long): IStream.Many<Pair<IPropertyReference, String>> {
        return tree.getProperties(sourceId)
    }

    override fun getAllReferenceTargetRefs(sourceId: Long): IStream.Many<Pair<IReferenceLinkReference, INodeReference>> {
        return tree.getReferenceTargets(sourceId)
    }

    override fun getReferenceTarget(sourceId: Long, role: IReferenceLinkReference): IStream.ZeroOrOne<INodeReference> {
        return tree.getReferenceTarget(sourceId, role)
    }

    override fun getReferenceRoles(sourceId: Long): IStream.Many<IReferenceLinkReference> {
        return tree.getReferenceRoles(sourceId)
    }

    override fun deleteNodes(nodeIds: LongArray): IStream.One<IAsyncMutableTree> {
        return tree.mutate(nodeIds.map { MutationParameters.Remove(it) }).wrap()
    }

    override fun moveChild(
        newParentId: Long,
        newRole: IChildLinkReference,
        newIndex: Int,
        childId: Long,
    ): IStream.One<IAsyncMutableTree> {
        return tree.mutate(MutationParameters.Move(newParentId, newRole, newIndex, listOf(childId))).wrap()
    }

    override fun setConcept(nodeId: Long, concept: ConceptReference): IStream.One<IAsyncMutableTree> {
        return tree.mutate(MutationParameters.Concept(nodeId, concept)).wrap()
    }

    override fun setPropertyValue(
        nodeId: Long,
        role: IPropertyReference,
        value: String?,
    ): IStream.One<IAsyncMutableTree> {
        return tree.mutate(MutationParameters.Property(nodeId, role, value)).wrap()
    }

    override fun addNewChildren(
        parentId: Long,
        role: IChildLinkReference,
        index: Int,
        newIds: LongArray,
        concepts: Array<ConceptReference>,
    ): IStream.One<IAsyncMutableTree> {
        return tree.mutate(MutationParameters.AddNew(parentId, role, index, newIds.zip(concepts))).wrap()
    }

    override fun setReferenceTarget(
        sourceId: Long,
        role: IReferenceLinkReference,
        target: INodeReference?,
    ): IStream.One<IAsyncMutableTree> {
        return tree.mutate(MutationParameters.Reference(sourceId, role, target)).wrap()
    }

    override fun setReferenceTarget(
        sourceId: Long,
        role: IReferenceLinkReference,
        targetId: Long,
    ): IStream.One<IAsyncMutableTree> {
        return tree.mutate(MutationParameters.Reference(sourceId, role, tree.createNodeReference(targetId))).wrap()
    }
}

fun ITree.asModelTree() = asAsyncTree().asModelTree()

fun IAsyncTree.asModelTree(): IGenericModelTree<Long> = when (this) {
    is ModelTreeAsLegacyAsyncTree -> tree
    else -> error("Unknown tree type: $this")
}

fun IGenericModelTree<Long>.asLegacyAsyncTree(): IAsyncMutableTree = ModelTreeAsLegacyAsyncTree(this)

fun IGenericModelTree<Long>.asLegacyTree(): ITree = asLegacyAsyncTree().asSynchronousTree()
