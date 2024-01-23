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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.modelix.model.area.IArea

/**
 * Reference to an [INode]-
 *
 * The relation between an [INodeReference] and an [INode] is n to 1.
 * Two [INodeReference]s that are not equal can resolve to the same [INode].
 */
@Serializable(with = NodeReferenceKSerializer::class)
interface INodeReference {
    /**
     * Tries to find the referenced node in the given [IArea].
     *
     * @param area area to be searched in
     * @return the node, or null if the node could not be found
     */
    @Deprecated("use .resolveIn(INodeResolutionScope)", ReplaceWith("resolveIn(area!!)"))
    fun resolveNode(area: IArea?): INode? = resolveIn(area as INodeResolutionScope)

    fun serialize(): String = INodeReferenceSerializer.serialize(this)
}

fun INodeReference.resolveInCurrentContext(): INode? {
    return resolveIn(INodeResolutionScope.getCurrentScope())
}

fun INodeReference.resolveIn(scope: INodeResolutionScope): INode? {
    try {
        if (this is NodeReference) {
            val deserialized = INodeReferenceSerializer.tryDeserialize(serialized)
            if (deserialized != null) return deserialized.resolveIn(scope)
        }
        @Suppress("DEPRECATION")
        return scope.resolveNode(this)
    } catch (ex: Exception) {
        throw RuntimeException("Failed to resolve $this", ex)
    }
}

class NodeReferenceKSerializer : KSerializer<INodeReference> {
    override fun deserialize(decoder: Decoder): INodeReference {
        val serialized = decoder.decodeString()
        return INodeReferenceSerializer.tryDeserialize(serialized) ?: SerializedNodeReference(serialized)
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("modelix.INodeReference", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: INodeReference) {
        encoder.encodeString(value.serialize())
    }
}
