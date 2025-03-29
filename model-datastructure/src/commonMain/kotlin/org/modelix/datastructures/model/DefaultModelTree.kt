package org.modelix.datastructures.model

import org.modelix.datastructures.IPersistentMap
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.NodeReference
import org.modelix.streams.IStream

class DefaultModelTree<NodeId : Any>(val nodesMap: IPersistentMap<NodeId, NodeObjectData<NodeId>>) : IModelTree<NodeId> {
    private fun resolveNode(nodeId: NodeId) = nodesMap.get(nodeId).exceptionIfEmpty { NodeNotFoundException(nodeId) }

    override fun containsNode(nodeId: NodeId): IStream.One<Boolean> {
        return nodesMap.get(nodeId).map { true }.firstOrDefault(false)
    }

    override fun getConceptReference(nodeId: NodeId): IStream.One<ConceptReference> {
        return resolveNode(nodeId).map { it.concept }
    }

    override fun getParent(nodeId: NodeId): IStream.ZeroOrOne<NodeId> {
        return resolveNode(nodeId).mapNotNull { it.parentId }
    }

    override fun getRoleInParent(nodeId: NodeId): IStream.ZeroOrOne<IChildLinkReference> {
        return resolveNode(nodeId).mapNotNull { it.roleInParent }
    }

    override fun getContainment(nodeId: NodeId): IStream.ZeroOrOne<Pair<NodeId, IChildLinkReference>> {
        return resolveNode(nodeId).mapNotNull {
            (it.parentId ?: return@mapNotNull null) to (it.roleInParent ?: return@mapNotNull null)
        }
    }

    override fun getProperty(nodeId: NodeId, role: IPropertyReference): IStream.ZeroOrOne<String> {
        return resolveNode(nodeId).mapNotNull { it.getProperty(role) }
    }

    override fun getPropertyRoles(nodeId: NodeId): IStream.Many<IPropertyReference> {
        return getProperties(nodeId).map { it.first }
    }

    override fun getProperties(nodeId: NodeId): IStream.Many<Pair<IPropertyReference, String>> {
        return resolveNode(nodeId).flatMapIterable { it.properties }
    }

    override fun getReferenceTarget(sourceId: NodeId, role: IReferenceLinkReference): IStream.ZeroOrOne<NodeReference> {
        return resolveNode(sourceId).mapNotNull { it.getReferenceTarget(role) }
    }

    override fun getReferenceRoles(sourceId: NodeId): IStream.Many<IReferenceLinkReference> {
        return getReferenceTargets(sourceId).map { it.first }
    }

    override fun getReferenceTargets(sourceId: NodeId): IStream.Many<Pair<IReferenceLinkReference, NodeReference>> {
        return resolveNode(sourceId).flatMapIterable { it.references }
    }

    override fun getChildren(parentId: NodeId): IStream.Many<NodeId> {
        return resolveNode(parentId).flatMapIterable { it.children }
    }

    private fun getRoleOfChild(childId: NodeId): IStream.One<IChildLinkReference> {
        return getRoleInParent(childId).assertNotEmpty { "Inconsistent containment relation." }
    }

    override fun getChildren(parentId: NodeId, role: IChildLinkReference): IStream.Many<NodeId> {
        return getChildren(parentId).filterBySingle {
            getRoleOfChild(it).map { it.matches(role) }
        }
    }

    override fun getChildRoles(parentId: NodeId): IStream.Many<IChildLinkReference> {
        return getChildren(parentId).flatMap { getRoleOfChild(it) }.distinct()
    }

    override fun getChildrenAndRoles(parentId: NodeId): IStream.Many<Pair<IChildLinkReference, IStream.Many<NodeId>>> {
        return getChildren(parentId).flatMap { childId ->
            getRoleOfChild(childId).map { role -> role to childId }
        }.toList().flatMapIterable {
            it.groupBy { it.first }.map { it.key to IStream.many(it.value.map { it.second }) }
        }
    }

    override fun mutate(operations: Iterable<MutationParameters<NodeId>>): IStream.One<DefaultModelTree<NodeId>> {
        // TODO bulk apply operations to improve performance
        return operations.fold(IStream.of(this)) { tree, operation ->
            tree.flatMapOne { it.mutate(operation) }
        }
    }

    override fun mutate(operation: MutationParameters<NodeId>): IStream.One<DefaultModelTree<NodeId>> {
        val newMap: IStream.One<IPersistentMap<NodeId, NodeObjectData<NodeId>>> = when (operation) {
            is MutationParameters.AddNew<NodeId> -> TreeMutator<NodeId>(this).addNewChildren(
                parentId = operation.nodeId,
                role = operation.role,
                index = operation.index,
                newIds = operation.newIdAndConcept.map { it.first },
                concepts = operation.newIdAndConcept.map { it.second },
            )

            is MutationParameters.Move<NodeId> -> TODO()
            is MutationParameters.Concept<NodeId> -> TODO()
            is MutationParameters.Property<NodeId> -> TreeMutator<NodeId>(this).setPropertyValue(
                nodeId = operation.nodeId,
                role = operation.role,
                value = operation.value,
            )

            is MutationParameters.Reference<NodeId> -> TODO()
            is MutationParameters.Remove<NodeId> -> TODO()
        }
        return newMap.map { DefaultModelTree<NodeId>(it) }
    }
}

class NodeNotFoundException(nodeId: Any?) : RuntimeException("Node doesn't exist: $nodeId")
