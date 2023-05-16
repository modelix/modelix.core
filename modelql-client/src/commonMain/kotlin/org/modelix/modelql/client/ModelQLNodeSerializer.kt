package org.modelix.modelql.client

import org.modelix.model.api.*
import org.modelix.modelql.modelapi.NodeKSerializer

class ModelQLNodeSerializer(val client: ModelQLClient) : NodeKSerializer() {
    override fun createNode(ref: SerializedNodeReference): INode {
        return ModelQLNodeWithConceptQuery(client, ref)
    }

    override fun createNode(ref: SerializedNodeReference, concept: ConceptReference?): INode {
        return ModelQLNodeWithKnownConcept(client, ref, concept)
    }
}