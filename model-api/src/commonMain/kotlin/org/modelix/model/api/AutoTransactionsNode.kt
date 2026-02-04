package org.modelix.model.api

class AutoTransactionsNode(val node: IWritableNode) : IWritableNode {
    private fun IWritableNode.wrap() = AutoTransactionsNode(this)
    private fun List<IWritableNode>.wrap() = map { it.wrap() }
    private fun IWritableNode.unwrap() = if (this is AutoTransactionsNode) this.node else this

    private fun <R> read(body: () -> R): R = node.getModel().executeRead(body)
    private fun <R> write(body: () -> R): R = node.getModel().executeWrite(body)

    override fun getModel(): IMutableModel {
        return AutoTransactionsModel(node.getModel())
    }

    override fun getAllChildren(): List<IWritableNode> {
        return read { node.getAllChildren().wrap() }
    }

    override fun getChildren(role: IChildLinkReference): List<IWritableNode> {
        return read { node.getChildren(role).wrap() }
    }

    override fun getLocalReferenceTarget(role: IReferenceLinkReference): IWritableNode? {
        return read { node.getLocalReferenceTarget(role)?.wrap() }
    }

    override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>> {
        return read { getAllReferenceTargets().map { it.first to it.second.wrap() } }
    }

    override fun getParent(): IWritableNode? {
        return read { node.getParent()?.wrap() }
    }

    override fun changeConcept(newConcept: ConceptReference): IWritableNode {
        return write { node.changeConcept(newConcept).wrap() }
    }

    override fun setPropertyValue(property: IPropertyReference, value: String?) {
        return write { node.setPropertyValue(property, value) }
    }

    override fun moveChild(
        role: IChildLinkReference,
        index: Int,
        child: IWritableNode,
    ) {
        return write { node.moveChild(role, index, child.unwrap()) }
    }

    override fun removeChild(child: IWritableNode) {
        return write { node.removeChild(child.unwrap()) }
    }

    override fun addNewChild(
        role: IChildLinkReference,
        index: Int,
        concept: ConceptReference,
    ): IWritableNode {
        return write { node.addNewChild(role, index, concept).wrap() }
    }

    override fun addNewChildren(
        role: IChildLinkReference,
        index: Int,
        concepts: List<ConceptReference>,
    ): List<IWritableNode> {
        return write { node.addNewChildren(role, index, concepts).wrap() }
    }

    override fun setReferenceTarget(
        role: IReferenceLinkReference,
        target: IWritableNode?,
    ) {
        return write { node.setReferenceTarget(role, target?.unwrap()) }
    }

    override fun setReferenceTargetRef(
        role: IReferenceLinkReference,
        target: INodeReference?,
    ) {
        return write { node.setReferenceTargetRef(role, target) }
    }

    override fun isValid(): Boolean {
        return read { node.isValid() }
    }

    override fun getNodeReference(): INodeReference {
        return read { node.getNodeReference() }
    }

    override fun getConcept(): IConcept {
        return read { node.getConcept() }
    }

    override fun getConceptReference(): ConceptReference {
        return read { node.getConceptReference() }
    }

    override fun getContainmentLink(): IChildLinkReference {
        return read { node.getContainmentLink() }
    }

    override fun getPropertyValue(property: IPropertyReference): String? {
        return read { node.getPropertyValue(property) }
    }

    override fun getPropertyLinks(): List<IPropertyReference> {
        return read { node.getPropertyLinks() }
    }

    override fun getAllProperties(): List<Pair<IPropertyReference, String>> {
        return read { node.getAllProperties() }
    }

    override fun getReferenceTargetRef(role: IReferenceLinkReference): INodeReference? {
        return read { node.getReferenceTargetRef(role) }
    }

    override fun getReferenceLinks(): List<IReferenceLinkReference> {
        return read { node.getReferenceLinks() }
    }

    override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>> {
        return read { node.getAllReferenceTargetRefs() }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AutoTransactionsNode

        if (node != other.node) return false

        return true
    }

    override fun hashCode(): Int {
        return node.hashCode()
    }
}

fun IWritableNode.withAutoTransactions() = AutoTransactionsNode(this)

class AutoTransactionsModel(val model: IMutableModel) : IMutableModel {
    override fun getRootNode(): IWritableNode = AutoTransactionsNode(model.getRootNode())

    override fun getRootNodes(): List<IWritableNode> = model.getRootNodes().map { AutoTransactionsNode(it) }

    override fun tryResolveNode(ref: INodeReference): IWritableNode? {
        return model.tryResolveNode(ref)?.let { AutoTransactionsNode(it) }
    }

    override fun <R> executeRead(body: () -> R): R = model.executeRead(body)

    override fun <R> executeWrite(body: () -> R): R = model.executeWrite(body)

    override fun canRead(): Boolean = model.canRead()

    override fun canWrite(): Boolean = model.canWrite()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AutoTransactionsModel

        return model == other.model
    }

    override fun hashCode(): Int {
        return model.hashCode()
    }
}

fun IMutableModel.withAutoTransactions(): IMutableModel = AutoTransactionsModel(this)
