package org.modelix.model.mutable

import org.modelix.model.TreeId
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.INodeReference
import org.modelix.model.api.PNodeReference

interface INodeIdGenerator<NodeId> {
    fun generate(parentNode: NodeId, role: IChildLinkReference, concept: ConceptReference): NodeId
}

class DummyIdGenerator<NodeId>() : INodeIdGenerator<NodeId> {
    override fun generate(parentNode: NodeId, role: IChildLinkReference, concept: ConceptReference): NodeId {
        throw UnsupportedOperationException("Creating nodes with new ID is not supported")
    }
}

class ModelixIdGenerator(val int64Generator: IIdGenerator, val treeId: TreeId) : INodeIdGenerator<INodeReference> {
    override fun generate(parentNode: INodeReference, role: IChildLinkReference, concept: ConceptReference): INodeReference {
        return PNodeReference(int64Generator.generate(), treeId.id)
    }
}

class Int64IdGenerator(val int64Generator: IIdGenerator) : INodeIdGenerator<Long> {
    override fun generate(parentNode: Long, role: IChildLinkReference, concept: ConceptReference): Long {
        return int64Generator.generate()
    }
}
