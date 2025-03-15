package org.modelix.model.async

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

    private fun IStream.One<IAsyncMutableTree>.getTree() = getSynchronous().asSynchronousTree()

    override fun asAsyncTree(): IAsyncMutableTree {
        return asyncTree
    }

    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?): ITree {
        return asyncTree.addNewChildren(
            parentId,
            IChildLinkReference.fromUnclassifiedString(role),
            index,
            longArrayOf(childId),
            arrayOf((concept ?: NullConcept).getReference().let { it as ConceptReference }),
        ).getTree()
    }

    override fun usesRoleIds(): Boolean {
        return (asyncTree as? AsyncTree)?.treeData?.usesRoleIds != false
    }

    private fun IPropertyReference.getKey() = if (usesRoleIds()) getIdOrName() else getNameOrId()
    private fun IReferenceLinkReference.getKey() = if (usesRoleIds()) getIdOrName() else getNameOrId()
    private fun IChildLinkReference.getKey() = if (usesRoleIds()) getIdOrNameOrNull() else getNameOrIdOrNull()

    override fun getId(): String? {
        return (asyncTree as AsyncTree).treeData.id
    }

    override fun visitChanges(oldVersion: ITree, visitor: ITreeChangeVisitor) {
        return asyncTree.getChanges(oldVersion.asAsyncTree(), visitor !is ITreeChangeVisitorEx).iterateSynchronous {
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
        return asyncTree.containsNode(nodeId).getSynchronous()
    }

    override fun getProperty(nodeId: Long, role: String): String? {
        return asyncTree.getPropertyValue(nodeId, IPropertyReference.fromUnclassifiedString(role)).orNull().getSynchronous()
    }

    override fun getChildren(parentId: Long, role: String?): Iterable<Long> {
        return asyncTree.getChildren(parentId, IChildLinkReference.fromUnclassifiedString(role)).toList().getSynchronous()
    }

    override fun getConcept(nodeId: Long): IConcept? {
        return getConceptReference(nodeId)?.resolve()
    }

    override fun getConceptReference(nodeId: Long): IConceptReference? {
        return asyncTree.getConceptReference(nodeId).getSynchronous().takeIf { it != NullConcept.getReference() }
    }

    override fun getParent(nodeId: Long): Long {
        return asyncTree.getParent(nodeId).orNull().getSynchronous() ?: 0L
    }

    override fun getRole(nodeId: Long): String? {
        return asyncTree.getRole(nodeId).map { it.getKey() }.getSynchronous()
    }

    override fun setProperty(nodeId: Long, role: String, value: String?): ITree {
        return asyncTree.setPropertyValue(nodeId, IPropertyReference.fromUnclassifiedString(role), value).getTree()
    }

    override fun getReferenceTarget(sourceId: Long, role: String): INodeReference? {
        return asyncTree.getReferenceTarget(sourceId, IReferenceLinkReference.fromUnclassifiedString(role)).orNull().getSynchronous()
    }

    override fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?): ITree {
        return asyncTree.setReferenceTarget(sourceId, IReferenceLinkReference.fromUnclassifiedString(role), target).getTree()
    }

    override fun getReferenceRoles(sourceId: Long): Iterable<String> {
        return asyncTree.getReferenceRoles(sourceId)
            .map { it.getKey() }
            .toList()
            .getSynchronous()
    }

    override fun getPropertyRoles(sourceId: Long): Iterable<String> {
        return asyncTree.getPropertyRoles(sourceId)
            .map { it.getKey() }
            .toList()
            .getSynchronous()
    }

    override fun getChildRoles(sourceId: Long): Iterable<String?> {
        return asyncTree.getChildRoles(sourceId)
            .map { it.getKey() }
            .toList()
            .getSynchronous()
    }

    override fun getAllChildren(parentId: Long): Iterable<Long> {
        return asyncTree.getAllChildren(parentId).toList().getSynchronous()
    }

    override fun moveChild(newParentId: Long, newRole: String?, newIndex: Int, childId: Long): ITree {
        return asyncTree.moveChild(newParentId, IChildLinkReference.fromUnclassifiedString(newRole), newIndex, childId).getTree()
    }

    override fun addNewChild(
        parentId: Long,
        role: String?,
        index: Int,
        childId: Long,
        concept: IConceptReference?,
    ): ITree {
        return asyncTree.addNewChildren(
            parentId,
            IChildLinkReference.fromUnclassifiedString(role),
            index,
            longArrayOf(childId),
            arrayOf((concept ?: NullConcept.getReference()) as ConceptReference),
        ).getTree()
    }

    override fun addNewChildren(
        parentId: Long,
        role: String?,
        index: Int,
        newIds: LongArray,
        concepts: Array<IConcept?>,
    ): ITree {
        return asyncTree.addNewChildren(
            parentId,
            IChildLinkReference.fromUnclassifiedString(role),
            index,
            newIds,
            concepts.map { (it ?: NullConcept).getReference() as ConceptReference }.toTypedArray(),
        ).getTree()
    }

    override fun addNewChildren(
        parentId: Long,
        role: String?,
        index: Int,
        newIds: LongArray,
        concepts: Array<IConceptReference?>,
    ): ITree {
        return asyncTree.addNewChildren(
            parentId,
            IChildLinkReference.fromUnclassifiedString(role),
            index,
            newIds,
            concepts.map { (it ?: NullConcept.getReference()) as ConceptReference }.toTypedArray(),
        ).getTree()
    }

    override fun deleteNode(nodeId: Long): ITree {
        return asyncTree.deleteNodes(longArrayOf(nodeId)).getTree()
    }

    override fun deleteNodes(nodeIds: LongArray): ITree {
        return asyncTree.deleteNodes(nodeIds).getTree()
    }

    override fun setConcept(nodeId: Long, concept: IConceptReference?): ITree {
        return asyncTree.setConcept(nodeId, (concept ?: NullConcept.getReference()) as ConceptReference).getTree()
    }
}
