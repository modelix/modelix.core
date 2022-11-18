/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License. 
 */

package org.modelix.model.lazy

import org.modelix.model.api.INodeReference

@Deprecated("use org.modelix.model.api.INodeReferenceSerializer")
interface INodeReferenceSerializer : org.modelix.model.api.INodeReferenceSerializer {

    override fun serialize(ref: INodeReference): String?
    override fun deserialize(serialized: String): INodeReference?

    companion object {

        fun register(serializer: INodeReferenceSerializer) {
            org.modelix.model.api.INodeReferenceSerializer.register(serializer)
        }

        fun unregister(serializer: INodeReferenceSerializer) {
            org.modelix.model.api.INodeReferenceSerializer.unregister(serializer)
        }

        fun serialize(ref: INodeReference): String {
            return org.modelix.model.api.INodeReferenceSerializer.serialize(ref)
        }

        fun deserialize(serialized: String): INodeReference {
            return org.modelix.model.api.INodeReferenceSerializer.deserialize(serialized)
        }
    }
}
