package org.modelix.datastructures.model

import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.LongDataTypeConfiguration
import org.modelix.datastructures.objects.Object
import org.modelix.model.TreeId
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.LocalPNodeReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.persistent.CPTree
import org.modelix.streams.IStream
import org.modelix.streams.IStreamExecutor
import org.modelix.streams.mapFirst
import org.modelix.streams.mapSecond
import kotlin.jvm.JvmName

class NodeReferenceAsLongModelTree(tree: IGenericModelTree<INodeReference>) : IdTranslatingModelTree<Long, INodeReference>(tree) {
    override fun Long.toInternal(): INodeReference = PNodeReference(this, getId().id)
    override fun INodeReference.toExternal(): Long = extractInt64Id(getId())
    override fun getNodeIdType(): IDataTypeConfiguration<Long> = LongDataTypeConfiguration()
    override fun getRootNodeId() = tree.getRootNodeId().toExternal()

    override fun wrap(newTree: IGenericModelTree<INodeReference>): IdTranslatingModelTree<Long, INodeReference> {
        return NodeReferenceAsLongModelTree(newTree)
    }

    override fun createNodeReference(nodeId: Long): INodeReference {
        return nodeId.toInternal()
    }
}

class LongAsNodeReferenceModelTree(tree: IGenericModelTree<Long>) : IdTranslatingModelTree<INodeReference, Long>(tree) {
    override fun INodeReference.toInternal(): Long = extractInt64Id(getId())
    override fun Long.toExternal(): INodeReference = PNodeReference(this, getId().id)

    override fun getNodeIdType(): IDataTypeConfiguration<INodeReference> = NodeReferenceDataTypeConfig()
    override fun getRootNodeId() = tree.getRootNodeId().toExternal()

    override fun wrap(newTree: IGenericModelTree<Long>): IdTranslatingModelTree<INodeReference, Long> {
        return LongAsNodeReferenceModelTree(newTree)
    }

    override fun createNodeReference(nodeId: INodeReference): INodeReference {
        return nodeId
    }
}

abstract class IdTranslatingModelTree<ExternalId, InternalId>(val tree: IGenericModelTree<InternalId>) : IGenericModelTree<ExternalId> {

    protected abstract fun ExternalId.toInternal(): InternalId
    protected abstract fun InternalId.toExternal(): ExternalId
    protected abstract fun wrap(newTree: IGenericModelTree<InternalId>): IdTranslatingModelTree<ExternalId, InternalId>

    private fun IStream.One<InternalId>.toExternal() = map { it.toExternal() }
    private fun IStream.ZeroOrOne<InternalId>.toExternal() = map { it.toExternal() }
    private fun IStream.Many<InternalId>.toExternal() = map { it.toExternal() }

    override fun asObject(): Object<CPTree> = tree.asObject()
    override fun getId(): TreeId = tree.getId()

    override fun containsNode(nodeId: ExternalId) = tree.containsNode(nodeId.toInternal())

    override fun getConceptReference(nodeId: ExternalId) = tree.getConceptReference(nodeId.toInternal())

    override fun getParent(nodeId: ExternalId) = tree.getParent(nodeId.toInternal()).toExternal()

    override fun getRoleInParent(nodeId: ExternalId) = tree.getRoleInParent(nodeId.toInternal())

    override fun getContainment(nodeId: ExternalId) = tree.getContainment(nodeId.toInternal()).mapFirst { it.toExternal() }

    override fun getProperty(
        nodeId: ExternalId,
        role: IPropertyReference,
    ): IStream.ZeroOrOne<String> {
        return tree.getProperty(nodeId.toInternal(), role)
    }

    override fun getPropertyRoles(nodeId: ExternalId) = tree.getPropertyRoles(nodeId.toInternal())

    override fun getProperties(nodeId: ExternalId) = tree.getProperties(nodeId.toInternal())

    override fun getReferenceTarget(sourceId: ExternalId, role: IReferenceLinkReference): IStream.ZeroOrOne<INodeReference> {
        return tree.getReferenceTarget(sourceId.toInternal(), role)
    }

    override fun getReferenceRoles(sourceId: ExternalId): IStream.Many<IReferenceLinkReference> {
        return tree.getReferenceRoles(sourceId.toInternal())
    }

    override fun getReferenceTargets(sourceId: ExternalId): IStream.Many<Pair<IReferenceLinkReference, INodeReference>> {
        return tree.getReferenceTargets(sourceId.toInternal())
    }

    override fun getChildren(parentId: ExternalId): IStream.Many<ExternalId> {
        return tree.getChildren(parentId.toInternal()).map { it.toExternal() }
    }

    override fun getChildren(
        parentId: ExternalId,
        role: IChildLinkReference,
    ): IStream.Many<ExternalId> {
        return tree.getChildren(parentId.toInternal(), role).map { it.toExternal() }
    }

    override fun getChildRoles(parentId: ExternalId): IStream.Many<IChildLinkReference> {
        return tree.getChildRoles(parentId.toInternal())
    }

    override fun getChildrenAndRoles(parentId: ExternalId): IStream.Many<Pair<IChildLinkReference, IStream.Many<ExternalId>>> {
        return tree.getChildrenAndRoles(parentId.toInternal()).mapSecond { it.map { it.toExternal() } }
    }

    override fun mutate(operations: Iterable<MutationParameters<ExternalId>>): IStream.One<IGenericModelTree<ExternalId>> {
        return tree.mutate(
            operations.map<MutationParameters<ExternalId>, MutationParameters<InternalId>> {
                when (it) {
                    is MutationParameters.AddNew<ExternalId> -> MutationParameters.AddNew(
                        nodeId = it.nodeId.toInternal(),
                        role = it.role,
                        index = it.index,
                        newIdAndConcept = it.newIdAndConcept.map { it.first.toInternal() to it.second },
                    )
                    is MutationParameters.Move<ExternalId> -> MutationParameters.Move(
                        nodeId = it.nodeId.toInternal(),
                        role = it.role,
                        index = it.index,
                        existingChildIds = it.existingChildIds.map { it.toInternal() },
                    )
                    is MutationParameters.Concept<ExternalId> -> MutationParameters.Concept(
                        nodeId = it.nodeId.toInternal(),
                        concept = it.concept,
                    )
                    is MutationParameters.Property<ExternalId> -> MutationParameters.Property(
                        nodeId = it.nodeId.toInternal(),
                        role = it.role,
                        value = it.value,
                    )
                    is MutationParameters.Reference<ExternalId> -> MutationParameters.Reference(
                        nodeId = it.nodeId.toInternal(),
                        role = it.role,
                        target = it.target,
                    )
                    is MutationParameters.Remove<ExternalId> -> MutationParameters.Remove(
                        nodeId = it.nodeId.toInternal(),
                    )
                }
            },
        ).map { wrap(it) }
    }

    override fun getStreamExecutor(): IStreamExecutor {
        return tree.getStreamExecutor()
    }

    override fun getChanges(
        oldVersion: IGenericModelTree<ExternalId>,
        changesOnly: Boolean,
    ): IStream.Many<ModelChangeEvent<ExternalId>> {
        oldVersion as IdTranslatingModelTree<ExternalId, InternalId>
        return tree.getChanges(oldVersion.tree, changesOnly).map { event: ModelChangeEvent<InternalId> ->
            when (event) {
                is ConceptChangedEvent<InternalId> -> ConceptChangedEvent(event.nodeId.toExternal())
                is ContainmentChangedEvent<InternalId> -> ContainmentChangedEvent(event.nodeId.toExternal())
                is NodeAddedEvent<InternalId> -> NodeAddedEvent(event.nodeId.toExternal())
                is NodeRemovedEvent<InternalId> -> NodeRemovedEvent(event.nodeId.toExternal())
                is ChildrenChangedEvent<InternalId> -> ChildrenChangedEvent(event.nodeId.toExternal(), event.role)
                is PropertyChangedEvent<InternalId> -> PropertyChangedEvent(event.nodeId.toExternal(), event.role)
                is ReferenceChangedEvent<InternalId> -> ReferenceChangedEvent(event.nodeId.toExternal(), event.role)
            }
        }
    }
}

@JvmName("withIdTranslationToInt64")
fun IGenericModelTree<INodeReference>.withIdTranslation(): IGenericModelTree<Long> {
    return when (this) {
        is LongAsNodeReferenceModelTree -> this.tree
        else -> NodeReferenceAsLongModelTree(this)
    }
}

@JvmName("withIdTranslationToNodeReferences")
fun IGenericModelTree<Long>.withIdTranslation(): IGenericModelTree<INodeReference> {
    return when (this) {
        is NodeReferenceAsLongModelTree -> this.tree
        else -> LongAsNodeReferenceModelTree(this)
    }
}

fun INodeReference.extractInt64Id(expectedTree: TreeId): Long {
    LocalPNodeReference.tryConvert(this)?.let { return it.id }
    val converted = PNodeReference.tryConvert(this)
    if (converted != null) {
        require(converted.treeId == expectedTree.id) { "Not part of this tree ($expectedTree): $this" }
        return converted.id
    }
    throw IllegalArgumentException("Cannot access the node using the legacy API. Unsupported node ID type: $this")
}

fun INodeReference.toGlobal(treeId: TreeId): INodeReference {
    return LocalPNodeReference.tryConvert(this)?.toGlobal(treeId.id) ?: this
}
