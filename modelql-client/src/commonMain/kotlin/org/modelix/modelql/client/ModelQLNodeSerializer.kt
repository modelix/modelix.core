package org.modelix.modelql.client

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.SerializedNodeReference
import org.modelix.modelql.untyped.NodeKSerializer

class ModelQLNodeSerializer(val client: ModelQLClient) : NodeKSerializer() {
    override fun createNode(ref: SerializedNodeReference): INode {
        return ModelQLNodeWithConceptQuery(client, ref)
    }

    override fun createNode(ref: SerializedNodeReference, concept: ConceptReference?): INode {
        return ModelQLNodeWithKnownConcept(client, ref, concept)
    }
}
