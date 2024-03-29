/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
