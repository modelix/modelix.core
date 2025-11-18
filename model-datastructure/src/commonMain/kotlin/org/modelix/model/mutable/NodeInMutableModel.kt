package org.modelix.model.mutable

import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.ISyncTargetNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NewNodeSpec
import org.modelix.model.api.NullChildLinkReference
import org.modelix.model.api.resolve
import org.modelix.streams.IStream
import org.modelix.streams.ifEmpty
import org.modelix.streams.mapSecond
import org.modelix.streams.query

class NodeInMutableModel(
    val tree: IGenericMutableModelTree<INodeReference>,
    private val nodeId: INodeReference,
) : IWritableNode, ISyncTargetNode {

    private fun IWritableNode.unwrap() = (this as NodeInMutableModel).nodeId
    private fun INodeReference.wrap() = NodeInMutableModel(tree, this)
    private fun IStream.Many<INodeReference>.wrap() = map { it.wrap() }
    private fun IStream.ZeroOrOne<INodeReference>.wrap() = map { it.wrap() }
    private fun IStream.One<INodeReference>.wrap() = map { it.wrap() }
    private fun <R> query(body: (IGenericModelTree<INodeReference>) -> IStream.One<R>): R {
        val persistentTree = tree.getTransaction().tree
        return persistentTree.query { body(persistentTree) }
    }
    private fun mutate(parameters: MutationParameters<INodeReference>) {
        tree.getWriteTransaction().mutate(parameters)
    }
    private fun getIdGenerator() = tree.getIdGenerator()

    override fun getModel(): IMutableModel {
        return tree.asModel()
    }

    override fun getAllChildren(): List<IWritableNode> {
        return query { it.getChildren(nodeId).wrap().toList() }
    }

    override fun getChildren(role: IChildLinkReference): List<IWritableNode> {
        return query { it.getChildren(nodeId, role).wrap().toList() }
    }

    override fun getLocalReferenceTarget(role: IReferenceLinkReference): IWritableNode? {
        return query { it.getReferenceTarget(nodeId, role).wrap().filter { it.isValid() }.orNull() }
    }

    override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>> {
        return query { it.getReferenceTargets(nodeId).mapSecond { it.wrap() }.toList() }
    }

    override fun getParent(): IWritableNode? {
        return query { it.getParent(nodeId).wrap().orNull() }
    }

    override fun changeConcept(newConcept: ConceptReference): IWritableNode {
        mutate(MutationParameters.Concept<INodeReference>(nodeId, newConcept))
        return this
    }

    override fun setPropertyValue(property: IPropertyReference, value: String?) {
        mutate(MutationParameters.Property<INodeReference>(nodeId, property, value))
    }

    override fun moveChild(role: IChildLinkReference, index: Int, child: IWritableNode) {
        mutate(MutationParameters.Move<INodeReference>(nodeId, role, index, listOf(child.unwrap())))
    }

    override fun removeChild(child: IWritableNode) {
        mutate(MutationParameters.Remove<INodeReference>(child.unwrap()))
    }

    override fun addNewChild(role: IChildLinkReference, index: Int, concept: ConceptReference): IWritableNode {
        val newId = getIdGenerator().generate(nodeId)
        mutate(
            MutationParameters.AddNew<INodeReference>(
                nodeId,
                role,
                index,
                listOf(newId to concept),
            ),
        )
        return newId.wrap()
    }

    override fun addNewChildren(role: IChildLinkReference, index: Int, concepts: List<ConceptReference>): List<IWritableNode> {
        val newIdAndConcept = concepts.map { getIdGenerator().generate(nodeId) to it }
        mutate(
            MutationParameters.AddNew<INodeReference>(
                nodeId,
                role,
                index,
                newIdAndConcept,
            ),
        )
        return newIdAndConcept.map { it.first.wrap() }
    }

    override fun syncNewChildren(role: IChildLinkReference, index: Int, specs: List<NewNodeSpec>): List<IWritableNode> {
        val newIdAndConcept = specs.map { spec ->
            requireNotNull(spec.preferredOrCurrentRef) { "Node ID required: $spec" } to spec.conceptRef
        }
        mutate(
            MutationParameters.AddNew<INodeReference>(
                nodeId,
                role,
                index,
                newIdAndConcept,
            ),
        )
        return newIdAndConcept.map { it.first.wrap() }
    }

    override fun setReferenceTarget(role: IReferenceLinkReference, target: IWritableNode?) {
        mutate(MutationParameters.Reference<INodeReference>(nodeId, role, target?.unwrap()))
    }

    override fun setReferenceTargetRef(role: IReferenceLinkReference, target: INodeReference?) {
        mutate(MutationParameters.Reference<INodeReference>(nodeId, role, target))
    }

    override fun isValid(): Boolean {
        return query { it.containsNode(nodeId) }
    }

    override fun getNodeReference(): INodeReference {
        return nodeId
    }

    override fun getConcept(): IConcept {
        return getConceptReference().resolve()
    }

    override fun getConceptReference(): ConceptReference {
        return query { it.getConceptReference(nodeId) }
    }

    override fun getContainmentLink(): IChildLinkReference {
        return query { it.getContainment(nodeId).map { it.second }.ifEmpty { NullChildLinkReference } }
    }

    override fun getPropertyValue(property: IPropertyReference): String? {
        return query { it.getProperty(nodeId, property).orNull() }
    }

    override fun getPropertyLinks(): List<IPropertyReference> {
        return query { it.getPropertyRoles(nodeId).toList() }
    }

    override fun getAllProperties(): List<Pair<IPropertyReference, String>> {
        return query { it.getProperties(nodeId).toList() }
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): INodeReference? {
        return query { it.getReferenceTarget(nodeId, role).orNull() }
    }

    override fun getReferenceLinks(): List<IReferenceLinkReference> {
        return query { it.getReferenceRoles(nodeId).toList() }
    }

    override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>> {
        return query { it.getReferenceTargets(nodeId).toList() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as NodeInMutableModel

        if (tree != other.tree) return false
        if (nodeId != other.nodeId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tree.hashCode()
        result = 31 * result + nodeId.hashCode()
        return result
    }
}

fun IGenericMutableModelTree<INodeReference>.getRootNode(): IWritableNode {
    return runRead { NodeInMutableModel(this, getTransaction().tree.getRootNodeId()) }
}
