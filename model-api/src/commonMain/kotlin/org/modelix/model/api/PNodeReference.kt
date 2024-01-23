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

package org.modelix.model.api

import org.modelix.model.area.IArea

data class PNodeReference(val id: Long, val branchId: String) : INodeReference {
    init {
        PNodeReferenceSerializer.ensureRegistered()
    }
    override fun resolveNode(area: IArea?): INode? {
        return area?.resolveNode(this)
    }

    fun toLocal() = LocalPNodeReference(id)

    override fun serialize(): String {
        return "pnode:${id.toString(16)}@$branchId"
    }

    override fun toString(): String {
        return "PNodeReference_${id.toString(16)}@$branchId"
    }

    companion object {
        fun deserialize(serialized: String): PNodeReference {
            return requireNotNull(tryDeserialize(serialized)) {
                "Not a valid PNodeReference: $serialized"
            }
        }
        fun tryDeserialize(serialized: String): PNodeReference? {
            if (serialized.startsWith("pnode:") && serialized.contains('@')) {
                val withoutPrefix = serialized.substringAfter("pnode:")
                val parts = withoutPrefix.split('@', limit = 2)
                if (parts.size != 2) return null
                val nodeId = parts[0].toLongOrNull(16) ?: return null
                return PNodeReference(nodeId, parts[1])
            } else if (serialized.startsWith("modelix:") && serialized.contains('/')) {
                // This would be a nicer serialization format that isn't used yet, but supporting it already will make
                // future changes easier without breaking old versions of this library.
                //
                // Example:    modelix:25038f9e-e8ad-470a-9ae8-6978ed172184/1a5003b818f
                // Old format: pnode:1a5003b818f@25038f9e-e8ad-470a-9ae8-6978ed172184
                //
                // The 'modelix' prefix is more intuitive for a node stored inside a Modelix repository.
                // Having the repository ID first also feels more natural.
                val withoutPrefix = serialized.substringAfter("modelix:")
                val nodeIdStr = withoutPrefix.substringAfterLast('/')
                val branchId = withoutPrefix.substringBeforeLast('/')
                val nodeId = nodeIdStr.toLongOrNull(16)
                if (nodeId != null && branchId.isNotEmpty()) {
                    return PNodeReference(nodeId, branchId)
                }
            }
            return null
        }
    }
}

object PNodeReferenceSerializer : INodeReferenceSerializerEx {
    override val prefix = "pnode"
    override val supportedReferenceClasses = setOf(PNodeReference::class)

    init {
        INodeReferenceSerializer.register(this)
    }

    fun ensureRegistered() {
        // Is done in the init section. Calling this method just ensures that the object is initialized.
    }

    override fun serialize(ref: INodeReference): String {
        return (ref as PNodeReference).let { "${ref.id.toString(16)}@${ref.branchId}" }
    }

    override fun deserialize(serialized: String): INodeReference {
        val parts = serialized.split('@', limit = 2)
        return PNodeReference(parts[0].toLong(16), parts[1])
    }
}
