package org.modelix.model.api.async

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.ITree
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.plus

interface IAsyncTree {
    fun asSynchronousTree(): ITree
    fun getStreamExecutor(): IStreamExecutor

    fun getChanges(oldVersion: IAsyncTree, changesOnly: Boolean): IStream.Many<TreeChangeEvent>

    fun getConceptReference(nodeId: Long): IStream.One<ConceptReference>

    fun containsNode(nodeId: Long): IStream.One<Boolean>

    fun getParent(nodeId: Long): IStream.ZeroOrOne<Long>
    fun getRole(nodeId: Long): IStream.One<IChildLinkReference>

    fun getChildren(parentId: Long, role: IChildLinkReference): IStream.Many<Long>
    fun getChildRoles(sourceId: Long): IStream.Many<IChildLinkReference>
    fun getAllChildren(parentId: Long): IStream.Many<Long>

    fun getPropertyValue(nodeId: Long, role: IPropertyReference): IStream.ZeroOrOne<String>

    fun getPropertyRoles(sourceId: Long): IStream.Many<IPropertyReference>
    fun getAllPropertyValues(sourceId: Long): IStream.Many<Pair<IPropertyReference, String>>

    fun getAllReferenceTargetRefs(sourceId: Long): IStream.Many<Pair<IReferenceLinkReference, INodeReference>>
    fun getReferenceTarget(sourceId: Long, role: IReferenceLinkReference): IStream.ZeroOrOne<INodeReference>
    fun getReferenceRoles(sourceId: Long): IStream.Many<IReferenceLinkReference>
}

interface IAsyncMutableTree : IAsyncTree {
    fun deleteNodes(nodeIds: LongArray): IStream.One<IAsyncMutableTree>
    fun moveChild(newParentId: Long, newRole: IChildLinkReference, newIndex: Int, childId: Long): IStream.One<IAsyncMutableTree>
    fun setConcept(nodeId: Long, concept: ConceptReference): IStream.One<IAsyncMutableTree>

    fun setPropertyValue(nodeId: Long, role: IPropertyReference, value: String?): IStream.One<IAsyncMutableTree>

    fun addNewChildren(parentId: Long, role: IChildLinkReference, index: Int, newIds: LongArray, concepts: Array<ConceptReference>): IStream.One<IAsyncMutableTree>

    fun setReferenceTarget(sourceId: Long, role: IReferenceLinkReference, target: INodeReference?): IStream.One<IAsyncMutableTree>
    fun setReferenceTarget(sourceId: Long, role: IReferenceLinkReference, targetId: Long): IStream.One<IAsyncMutableTree>
}

fun IAsyncTree.getAncestors(nodeId: Long, includeSelf: Boolean): IStream.Many<Long> {
    return if (includeSelf) {
        IStream.of(nodeId) + getAncestors(nodeId, false)
    } else {
        getParent(nodeId).flatMap { getAncestors(it, true) }
    }
}

fun IAsyncTree.getDescendants(nodeId: Long, includeSelf: Boolean): IStream.Many<Long> {
    return if (includeSelf) getDescendantsAndSelf(nodeId) else getDescendants(nodeId)
}

fun IAsyncTree.getDescendants(nodeId: Long): IStream.Many<Long> {
    return getAllChildren(nodeId).flatMap { getDescendantsAndSelf(it) }
}

fun IAsyncTree.getDescendantsAndSelf(nodeId: Long): IStream.Many<Long> {
    return IStream.of(nodeId) + getDescendants(nodeId)
}
