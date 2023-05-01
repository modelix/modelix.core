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

    override fun toString(): String {
        return "PNodeReference_${id.toString(16)}@$branchId"
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
