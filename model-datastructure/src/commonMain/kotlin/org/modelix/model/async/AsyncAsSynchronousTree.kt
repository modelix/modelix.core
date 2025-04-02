package org.modelix.model.async

import org.modelix.datastructures.model.ModelTreeAsLegacyAsyncTree
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.async.ChildrenChangedEvent
import org.modelix.model.api.async.ConceptChangedEvent
import org.modelix.model.api.async.ContainmentChangedEvent
import org.modelix.model.api.async.IAsyncMutableTree
import org.modelix.model.api.async.NodeAddedEvent
import org.modelix.model.api.async.NodeRemovedEvent
import org.modelix.model.api.async.PropertyChangedEvent
import org.modelix.model.api.async.ReferenceChangedEvent
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.api.resolve
import org.modelix.streams.IStream

internal class AsyncAsSynchronousTree(val asyncTree: IAsyncMutableTree) : ITree {
    override fun asObject() = asyncTree.asObject()

    private fun getStreamExecutor() = asyncTree.getStreamExecutor()

    private fun queryTree(body: () -> IStream.One<IAsyncMutableTree>): ITree {
        return getStreamExecutor().query(body).asSynchronousTree()
    }

    override fun asAsyncTree(): IAsyncMutableTree {
        return asyncTree
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?): ITree {
        return queryTree {
            asyncTree.addNewChildren(
                parentId,
                IChildLinkReference.fromString(role),
                index,
                longArrayOf(childId),
                arrayOf((concept ?: NullConcept).getReference().let { it as ConceptReference }),
            )
        }
    }

    override fun usesRoleIds(): Boolean {
        return true
    }

    private fun IPropertyReference.getKey() = stringForLegacyApi()
    private fun IReferenceLinkReference.getKey() = stringForLegacyApi()
    private fun IChildLinkReference.getKey() = stringForLegacyApi()

    override fun getId(): String {
        return (asyncTree as ModelTreeAsLegacyAsyncTree).tree.getId().id
    }

    override fun visitChanges(oldVersion: ITree, visitor: ITreeChangeVisitor) {
        return getStreamExecutor().iterate({
            asyncTree.getChanges(oldVersion.asAsyncTree(), visitor !is ITreeChangeVisitorEx)
        }) {
            when (it) {
                is ConceptChangedEvent -> visitor.conceptChanged(it.nodeId)
                is ContainmentChangedEvent -> visitor.containmentChanged(it.nodeId)
                is NodeAddedEvent -> (visitor as ITreeChangeVisitorEx).nodeAdded(it.nodeId)
                is NodeRemovedEvent -> (visitor as ITreeChangeVisitorEx).nodeRemoved(it.nodeId)
                is ChildrenChangedEvent -> visitor.childrenChanged(it.nodeId, it.role.getKey())
                is PropertyChangedEvent -> visitor.propertyChanged(it.nodeId, it.role.getKey())
                is ReferenceChangedEvent -> visitor.referenceChanged(it.nodeId, it.role.getKey())
            }
        }
    }

    override fun containsNode(nodeId: Long): Boolean {
        return getStreamExecutor().query { asyncTree.containsNode(nodeId) }
    }

    override fun getProperty(nodeId: Long, role: String): String? {
        return getStreamExecutor().query { asyncTree.getPropertyValue(nodeId, IPropertyReference.fromString(role)).orNull() }
    }

    override fun getChildren(parentId: Long, role: String?): Iterable<Long> {
        return getStreamExecutor().query { asyncTree.getChildren(parentId, IChildLinkReference.fromString(role)).toList() }
    }

    override fun getConcept(nodeId: Long): IConcept? {
        return getConceptReference(nodeId)?.resolve()
    }

    override fun getConceptReference(nodeId: Long): IConceptReference? {
        return getStreamExecutor().query { asyncTree.getConceptReference(nodeId) }.takeIf { it != NullConcept.getReference() }
    }

    override fun getParent(nodeId: Long): Long {
        return getStreamExecutor().query { asyncTree.getParent(nodeId).orNull() } ?: 0L
    }

    override fun getRole(nodeId: Long): String? {
        return getStreamExecutor().query { asyncTree.getRole(nodeId).map { it.getKey() } }
    }

    override fun setProperty(nodeId: Long, role: String, value: String?): ITree {
        return queryTree { asyncTree.setPropertyValue(nodeId, IPropertyReference.fromString(role), value) }
    }

    override fun getReferenceTarget(sourceId: Long, role: String): INodeReference? {
        return getStreamExecutor().query { asyncTree.getReferenceTarget(sourceId, IReferenceLinkReference.fromString(role)).orNull() }
    }

    override fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?): ITree {
        return queryTree { asyncTree.setReferenceTarget(sourceId, IReferenceLinkReference.fromString(role), target) }
    }

    override fun getReferenceRoles(sourceId: Long): Iterable<String> {
        return getStreamExecutor().query {
            asyncTree.getReferenceRoles(sourceId)
                .map { it.getKey() }
                .toList()
        }
    }

    override fun getPropertyRoles(sourceId: Long): Iterable<String> {
        return getStreamExecutor().query {
            asyncTree.getPropertyRoles(sourceId)
                .map { it.getKey() }
                .toList()
        }
    }

    override fun getChildRoles(sourceId: Long): Iterable<String?> {
        return getStreamExecutor().query {
            asyncTree.getChildRoles(sourceId)
                .map { it.getKey() }
                .toList()
        }
    }

    override fun getAllChildren(parentId: Long): Iterable<Long> {
        return getStreamExecutor().query {
            asyncTree.getAllChildren(parentId).toList()
        }
    }

    override fun moveChild(newParentId: Long, newRole: String?, newIndex: Int, childId: Long): ITree {
        return queryTree { asyncTree.moveChild(newParentId, IChildLinkReference.fromString(newRole), newIndex, childId) }
    }

    override fun addNewChild(
        parentId: Long,
        role: String?,
        index: Int,
        childId: Long,
        concept: IConceptReference?,
    ): ITree {
        return queryTree {
            asyncTree.addNewChildren(
                parentId,
                IChildLinkReference.fromString(role),
                index,
                longArrayOf(childId),
                arrayOf((concept ?: NullConcept.getReference()) as ConceptReference),
            )
        }
    }

    override fun addNewChildren(
        parentId: Long,
        role: String?,
        index: Int,
        newIds: LongArray,
        concepts: Array<IConcept?>,
    ): ITree {
        return queryTree {
            asyncTree.addNewChildren(
                parentId,
                IChildLinkReference.fromString(role),
                index,
                newIds,
                concepts.map { (it ?: NullConcept).getReference() as ConceptReference }.toTypedArray(),
            )
        }
    }

    override fun addNewChildren(
        parentId: Long,
        role: String?,
        index: Int,
        newIds: LongArray,
        concepts: Array<IConceptReference?>,
    ): ITree {
        return queryTree {
            asyncTree.addNewChildren(
                parentId,
                IChildLinkReference.fromString(role),
                index,
                newIds,
                concepts.map { (it ?: NullConcept.getReference()) as ConceptReference }.toTypedArray(),
            )
        }
    }

    override fun deleteNode(nodeId: Long): ITree {
        return queryTree { asyncTree.deleteNodes(longArrayOf(nodeId)) }
    }

    override fun deleteNodes(nodeIds: LongArray): ITree {
        return queryTree { asyncTree.deleteNodes(nodeIds) }
    }

    override fun setConcept(nodeId: Long, concept: IConceptReference?): ITree {
        return queryTree { asyncTree.setConcept(nodeId, (concept ?: NullConcept.getReference()) as ConceptReference) }
    }
}
