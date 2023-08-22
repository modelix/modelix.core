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
package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.SNodePointer
import org.jetbrains.mps.openapi.model.SNodeReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.INodeReferenceSerializerEx
import kotlin.reflect.KClass

data class MPSNodeReference(val ref: SNodeReference) : INodeReference {

    companion object {

        init {
            INodeReferenceSerializer.register(MPSNodeReferenceSerializer)
        }

        fun tryConvert(ref: INodeReference): MPSNodeReference? {
            if (ref is MPSNodeReference) return ref
            val serialized = ref.serialize()
            val serializedMPSRef = when {
                serialized.startsWith("mps-node:") -> serialized.substringAfter("mps-node:")
                serialized.startsWith("mps:") -> serialized.substringAfter("mps:")
                else -> return null
            }
            return MPSNodeReference(SNodePointer.deserialize(serializedMPSRef))
        }
    }
}

fun INodeReference.toMPSNodeReference(): MPSNodeReference {
    return MPSNodeReference.tryConvert(this)
        ?: throw IllegalArgumentException("Not an MPS node reference: $this")
}

object MPSNodeReferenceSerializer : INodeReferenceSerializerEx {
    override val prefix = "mps"
    override val supportedReferenceClasses: Set<KClass<out INodeReference>> = setOf(MPSNodeReference::class)

    override fun serialize(ref: INodeReference): String {
        return (ref as MPSNodeReference).ref.nodeId.toString()
    }

    override fun deserialize(serialized: String): INodeReference {
        TODO("Not yet implemented")
    }
}
