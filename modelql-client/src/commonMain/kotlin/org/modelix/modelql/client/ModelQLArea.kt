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

import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.area.IArea
import org.modelix.model.area.IAreaListener
import org.modelix.model.area.IAreaReference

data class ModelQLArea(val client: ModelQLClient) : IArea {
    override fun getRoot(): INode {
        return ModelQLRootNode(client)
    }

    @Deprecated("use ILanguageRepository.resolveConcept")
    override fun resolveConcept(ref: IConceptReference): IConcept? {
        TODO("Not yet implemented")
    }

    override fun resolveNode(ref: INodeReference): INode? {
        if (ref is ModelQLRootNodeReference) return ModelQLRootNode(client)
        return ModelQLNodeWithConceptQuery(client, ref.toSerializedRef())
    }

    override fun resolveOriginalNode(ref: INodeReference): INode? {
        return ModelQLNodeWithConceptQuery(client, ref.toSerializedRef())
    }

    override fun resolveBranch(id: String): IBranch? {
        return null
    }

    override fun collectAreas(): List<IArea> {
        return listOf(this)
    }

    override fun getReference(): IAreaReference {
        TODO("Not yet implemented")
    }

    override fun resolveArea(ref: IAreaReference): IArea? {
        TODO("Not yet implemented")
    }

    override fun <T> executeRead(f: () -> T): T {
        return f()
    }

    override fun <T> executeWrite(f: () -> T): T {
        throw UnsupportedOperationException("readonly")
    }

    override fun canRead(): Boolean {
        return true
    }

    override fun canWrite(): Boolean {
        return false
    }

    override fun addListener(l: IAreaListener) {
        TODO("Not yet implemented")
    }

    override fun removeListener(l: IAreaListener) {
        TODO("Not yet implemented")
    }
}
