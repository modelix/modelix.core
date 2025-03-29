package org.modelix.datastructures.model

import org.modelix.datastructures.objects.Object
import org.modelix.model.TreeId
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.NodeReference
import org.modelix.model.api.NullChildLinkReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.async.IAsyncMutableTree
import org.modelix.model.api.toSerialized
import org.modelix.model.persistent.CPTree
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider
import org.modelix.streams.filterNotNull
import org.modelix.streams.ifEmpty

@Deprecated("There aren't any real implementations of IAsyncMutableTree anymore and this adapter should be unnecessary.")
private class LegacyAsyncTreeAsModelTree(val tree: IAsyncMutableTree) : IModelTree<Long>, IStreamExecutorProvider by tree {
    override fun asObject(): Object<CPTree> = tree.asObject() as Object<CPTree>

    override fun getId(): TreeId {
        return TreeId.fromLegacyId(tree.asSynchronousTree().getId()!!)
    }

    override fun createNodeReference(nodeId: Long): INodeReference {
        return PNodeReference(nodeId, tree.asSynchronousTree().getId()!!).toSerialized()
    }

    override fun containsNode(nodeId: Long): IStream.One<Boolean> {
        return tree.containsNode(nodeId)
    }

    override fun getConceptReference(nodeId: Long): IStream.One<ConceptReference> {
        return tree.getConceptReference(nodeId)
    }

    override fun getParent(nodeId: Long): IStream.ZeroOrOne<Long> {
        return tree.getParent(nodeId)
    }

    override fun getRoleInParent(nodeId: Long): IStream.ZeroOrOne<IChildLinkReference> {
        return tree.getRole(nodeId)
    }

    override fun getContainment(nodeId: Long): IStream.ZeroOrOne<Pair<Long, IChildLinkReference>> {
        return tree.getParent(nodeId).orNull().zipWith(tree.getRole(nodeId)) { parent, role ->
            parent?.let { it to role }
        }.filterNotNull()
    }

    override fun getProperty(nodeId: Long, role: IPropertyReference): IStream.ZeroOrOne<String> {
        return tree.getPropertyValue(nodeId, role)
    }

    override fun getPropertyRoles(nodeId: Long): IStream.Many<IPropertyReference> {
        return tree.getPropertyRoles(nodeId)
    }

    override fun getProperties(nodeId: Long): IStream.Many<Pair<IPropertyReference, String>> {
        return tree.getAllPropertyValues(nodeId)
    }

    override fun getReferenceTarget(sourceId: Long, role: IReferenceLinkReference): IStream.ZeroOrOne<NodeReference> {
        return tree.getReferenceTarget(sourceId, role).map { it.toSerialized() }
    }

    override fun getReferenceRoles(sourceId: Long): IStream.Many<IReferenceLinkReference> {
        return tree.getReferenceRoles(sourceId)
    }

    override fun getReferenceTargets(sourceId: Long): IStream.Many<Pair<IReferenceLinkReference, NodeReference>> {
        return tree.getAllReferenceTargetRefs(sourceId).map { it.first to it.second.toSerialized() }
    }

    override fun getChildren(parentId: Long): IStream.Many<Long> {
        return tree.getAllChildren(parentId)
    }

    override fun getChildren(parentId: Long, role: IChildLinkReference): IStream.Many<Long> {
        return tree.getChildren(parentId, role)
    }

    override fun getChildRoles(parentId: Long): IStream.Many<IChildLinkReference> {
        return tree.getChildRoles(parentId)
    }

    override fun getChildrenAndRoles(parentId: Long): IStream.Many<Pair<IChildLinkReference, IStream.Many<Long>>> {
        return getChildren(parentId).flatMap { childId ->
            getRoleInParent(childId).ifEmpty { NullChildLinkReference }.map { role -> role to childId }
        }.toList().flatMapIterable {
            it.groupBy { it.first }.map { it.key to IStream.many(it.value.map { it.second }) }
        }
    }

    private fun IStream.One<IAsyncMutableTree>.wrap() = map { LegacyAsyncTreeAsModelTree(it) }

    override fun mutate(operations: Iterable<MutationParameters<Long>>): IStream.One<IModelTree<Long>> {
        return operations.fold<MutationParameters<Long>, IStream.One<IModelTree<Long>>>(IStream.of(this)) { acc, op ->
            acc.flatMapOne { it.mutate(op) }
        }
    }

    override fun mutate(op: MutationParameters<Long>): IStream.One<LegacyAsyncTreeAsModelTree> {
        return when (op) {
            is MutationParameters.AddNew<Long> -> tree.addNewChildren(
                op.nodeId,
                op.role,
                op.index,
                op.newIdAndConcept.map { it.first }.toLongArray(),
                op.newIdAndConcept.map { it.second }.toTypedArray(),
            ).wrap()
            is MutationParameters.Move<Long> -> op.existingChildIds.reversed().fold(IStream.of(tree)) { acc, childId ->
                acc.flatMapOne { it.moveChild(op.nodeId, op.role, op.index, childId) }
            }.wrap()
            is MutationParameters.Concept<Long> -> tree.setConcept(op.nodeId, op.concept).wrap()
            is MutationParameters.Property<Long> -> tree.setPropertyValue(op.nodeId, op.role, op.value).wrap()
            is MutationParameters.Reference<Long> -> tree.setReferenceTarget(op.nodeId, op.role, op.target).wrap()
            is MutationParameters.Remove<Long> -> tree.deleteNodes(longArrayOf(op.nodeId)).wrap()
        }
    }

    override fun getChanges(
        oldVersion: IModelTree<Long>,
        changesOnly: Boolean,
    ): IStream.Many<ModelChangeEvent<Long>> {
        TODO("Not yet implemented")
    }
}
