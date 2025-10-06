package org.modelix.datastructures.model

import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.Object
import org.modelix.model.TreeId
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.persistent.CPTree
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutorProvider
import org.modelix.streams.plus

typealias IModelTree = IGenericModelTree<INodeReference>

/**
 * Persistent model implementation that supports bulk requests, bulk updates and non-Long node IDs.
 * Consistently using the streams API enables efficient lazy loading of model data.
 * The bulk update method [mutate] fixes performance issues during a model import.
 *
 * Supporting other data types for the node ID than just Long (64-bit integers) values, allows using the original ID
 * as the primary ID. This makes importing and updating models from source such as MPS easier and more efficient. No
 * need for maintaining a mapping between the MPS ID and the Modelix ID. Also, since there is no generated ID that has
 * a different value for each import, we don't have to check if a node already exists and reuse it. We can just run a
 * fresh import and then do a diff. All the data stored in the [IGenericModelTree] also exists in the original source and if it
 * didn't change, we will end up with the same hash.
 * With a prefix tree like the [org.modelix.datastructures.patricia.PatriciaTrie] we can even re-run the import for
 * individual MPS models very efficiently, because all the nodes in the same model have the same model ID as a common
 * prefix of their node ID and will end up in the same subtree of the trie data structure, giving us a single hash for
 * the imported MPS model.
 */
interface IGenericModelTree<NodeId> : IStreamExecutorProvider {
    fun asObject(): Object<CPTree>
    fun getNodeIdType(): IDataTypeConfiguration<NodeId>

    fun getId(): TreeId
    fun getRootNodeId(): NodeId
    fun createNodeReference(nodeId: NodeId): INodeReference

    fun containsNode(nodeId: NodeId): IStream.One<Boolean>
    fun getConceptReference(nodeId: NodeId): IStream.One<ConceptReference>

    fun getParent(nodeId: NodeId): IStream.ZeroOrOne<NodeId>
    fun getRoleInParent(nodeId: NodeId): IStream.ZeroOrOne<IChildLinkReference>
    fun getContainment(nodeId: NodeId): IStream.ZeroOrOne<Pair<NodeId, IChildLinkReference>>

    fun getProperty(nodeId: NodeId, role: IPropertyReference): IStream.ZeroOrOne<String>
    fun getPropertyRoles(nodeId: NodeId): IStream.Many<IPropertyReference>
    fun getProperties(nodeId: NodeId): IStream.Many<Pair<IPropertyReference, String>>

    fun getReferenceTarget(sourceId: NodeId, role: IReferenceLinkReference): IStream.ZeroOrOne<INodeReference>
    fun getReferenceRoles(sourceId: NodeId): IStream.Many<IReferenceLinkReference>
    fun getReferenceTargets(sourceId: NodeId): IStream.Many<Pair<IReferenceLinkReference, INodeReference>>

    fun getChildren(parentId: NodeId): IStream.Many<NodeId>
    fun getChildren(parentId: NodeId, role: IChildLinkReference): IStream.Many<NodeId>
    fun getChildRoles(parentId: NodeId): IStream.Many<IChildLinkReference>
    fun getChildrenAndRoles(parentId: NodeId): IStream.Many<Pair<IChildLinkReference, IStream.Many<NodeId>>>

    fun getChanges(oldVersion: IGenericModelTree<NodeId>, changesOnly: Boolean): IStream.Many<ModelChangeEvent<NodeId>>

    fun mutate(operations: Iterable<MutationParameters<NodeId>>): IStream.One<IGenericModelTree<NodeId>>

    fun mutate(operation: MutationParameters<NodeId>): IStream.One<IGenericModelTree<NodeId>> = mutate(listOf(operation))

    companion object {
        fun builder(): ModelTreeBuilder<Long> = ModelTreeBuilder.newWithInt64Ids()
    }
}

fun IGenericModelTree<*>.getHash() = asObject().getHash()

fun <NodeId> IGenericModelTree<NodeId>.getDescendants(nodeId: NodeId, includeSelf: Boolean): IStream.Many<NodeId> {
    return if (includeSelf) {
        IStream.of(nodeId).plus(getDescendants(nodeId, false))
    } else {
        getChildren(nodeId).flatMap { getDescendants(it, true) }
    }
}

fun <NodeId> IGenericModelTree<NodeId>.getAncestors(nodeId: NodeId, includeSelf: Boolean): IStream.Many<NodeId> {
    return if (includeSelf) {
        IStream.of(nodeId).plus(getAncestors(nodeId, false))
    } else {
        getParent(nodeId).flatMap { getAncestors(it, true) }
    }
}

fun <NodeId> IGenericModelTree<NodeId>.setProperty(nodeId: NodeId, role: IPropertyReference, value: String?): IStream.One<IGenericModelTree<NodeId>> =
    mutate(MutationParameters.Property(nodeId, role, value))

fun <NodeId> IGenericModelTree<NodeId>.setReferenceTarget(sourceId: NodeId, role: IReferenceLinkReference, target: INodeReference): IStream.One<IGenericModelTree<NodeId>> =
    mutate(MutationParameters.Reference(sourceId, role, target))

fun <NodeId> IGenericModelTree<NodeId>.moveNode(newParentId: NodeId, newRole: IChildLinkReference, newIndex: Int, childId: NodeId): IStream.One<IGenericModelTree<NodeId>> =
    mutate(MutationParameters.Move(newParentId, newRole, newIndex, listOf(childId)))

fun <NodeId> IGenericModelTree<NodeId>.addNewChild(parentId: NodeId, role: IChildLinkReference, index: Int, childId: NodeId, concept: ConceptReference): IStream.One<IGenericModelTree<NodeId>> =
    mutate(MutationParameters.AddNew(parentId, role, index, listOf(childId to concept)))

fun <NodeId> IGenericModelTree<NodeId>.removeNode(nodeId: NodeId): IStream.One<IGenericModelTree<NodeId>> =
    mutate(MutationParameters.Remove(nodeId))

fun <NodeId> IGenericModelTree<NodeId>.changeConcept(nodeId: NodeId, concept: ConceptReference): IStream.One<IGenericModelTree<NodeId>> =
    mutate(MutationParameters.Concept(nodeId, concept))
