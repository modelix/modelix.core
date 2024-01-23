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

package org.modelix.model.persistent

import org.modelix.model.lazy.COWArrays.copy
import org.modelix.model.lazy.COWArrays.insert
import org.modelix.model.lazy.COWArrays.removeAt
import org.modelix.model.lazy.COWArrays.set
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.CPNodeRef.Companion.fromString
import org.modelix.model.persistent.SerializationUtil.escape
import org.modelix.model.persistent.SerializationUtil.longFromHex
import org.modelix.model.persistent.SerializationUtil.longToHex
import org.modelix.model.persistent.SerializationUtil.unescape
import kotlin.jvm.JvmStatic

class CPNode private constructor(
    val id: Long,
    val concept: String?,
    val parentId: Long,
    val roleInParent: String?,
    private val childrenIds: LongArray,
    val propertyRoles: Array<String>,
    val propertyValues: Array<String>,
    val referenceRoles: Array<String>,
    val referenceTargets: Array<CPNodeRef>,
) : IKVValue {

    override var isWritten: Boolean = false

    override fun serialize(): String {
        val sb = StringBuilder()
        sb.append(longToHex(id))
        sb.append(Separators.LEVEL1)
        sb.append(escape(concept))
        sb.append(Separators.LEVEL1)
        sb.append(longToHex(parentId))
        sb.append(Separators.LEVEL1)
        sb.append(escape(roleInParent))
        sb.append(Separators.LEVEL1)
        sb.append(if (childrenIds.isEmpty()) "" else childrenIds.joinToString(Separators.LEVEL2) { longToHex(it) })
        sb.append(Separators.LEVEL1)
        propertyRoles.forEachIndexed { index, role ->
            if (index != 0) sb.append(Separators.LEVEL2)
            sb.append(escape(role)).append(Separators.MAPPING).append(escape(propertyValues[index]))
        }
        sb.append(Separators.LEVEL1)
        referenceRoles.forEachIndexed { index, role ->
            if (index != 0) sb.append(Separators.LEVEL2)
            sb.append(escape(role)).append(Separators.MAPPING).append(escape(referenceTargets[index].toString()))
        }
        return sb.toString()
    }

    fun getChildrenIds(): Iterable<Long> {
        return Iterable { childrenIds.iterator() }
    }

    val childrenIdArray: LongArray
        get() = copy(childrenIds)

    val childrenSize: Int
        get() = childrenIds.size

    override val hash: String by lazy(LazyThreadSafetyMode.PUBLICATION) { HashUtil.sha256(serialize()) }

    fun getChildId(index: Int): Long {
        return childrenIds[index]
    }

    fun getPropertyValue(role: String?): String? {
        val index = propertyRoles.asList().binarySearch(role)
        return if (index < 0) null else propertyValues[index]
    }

    fun getReferenceTarget(role: String?): CPNodeRef? {
        val index = referenceRoles.asList().binarySearch(role)
        return if (index < 0) null else referenceTargets[index]
    }

    fun withPropertyValue(role: String, value: String?): CPNode {
        var index = propertyRoles.asList().binarySearch(role)
        return if (value == null) {
            if (index < 0) {
                this
            } else {
                create(
                    id,
                    concept,
                    parentId,
                    roleInParent,
                    childrenIds,
                    removeAt(propertyRoles, index),
                    removeAt(propertyValues, index),
                    referenceRoles,
                    referenceTargets,
                )
            }
        } else {
            if (index < 0) {
                index = -(index + 1)
                create(
                    id,
                    concept,
                    parentId,
                    roleInParent,
                    childrenIds,
                    insert(propertyRoles, index, role),
                    insert(propertyValues, index, value),
                    referenceRoles,
                    referenceTargets,
                )
            } else {
                create(
                    id,
                    concept,
                    parentId,
                    roleInParent,
                    childrenIds,
                    propertyRoles,
                    set(propertyValues, index, value),
                    referenceRoles,
                    referenceTargets,
                )
            }
        }
    }

    fun withReferenceTarget(role: String, target: CPNodeRef?): CPNode {
        var index = referenceRoles.asList().binarySearch(role)
        return if (target == null) {
            if (index < 0) {
                this
            } else {
                create(
                    id, concept, parentId, roleInParent, childrenIds,
                    propertyRoles, propertyValues,
                    removeAt(referenceRoles, index), removeAt(referenceTargets, index),
                )
            }
        } else {
            if (index < 0) {
                index = -(index + 1)
                create(
                    id,
                    concept,
                    parentId,
                    roleInParent,
                    childrenIds,
                    propertyRoles,
                    propertyValues,
                    insert(referenceRoles, index, role),
                    insert(referenceTargets, index, target),
                )
            } else {
                create(
                    id,
                    concept,
                    parentId,
                    roleInParent,
                    childrenIds,
                    propertyRoles,
                    propertyValues,
                    referenceRoles,
                    set(referenceTargets, index, target),
                )
            }
        }
    }
    override fun getDeserializer(): (String) -> IKVValue = DESERIALIZER
    override fun getReferencedEntries(): List<KVEntryReference<IKVValue>> = listOf()

    companion object {
        val DESERIALIZER = { s: String -> deserialize(s) }

        @JvmStatic
        fun create(
            id: Long,
            concept: String?,
            parentId: Long,
            roleInParent: String?,
            childrenIds: LongArray,
            propertyRoles: Array<String>,
            propertyValues: Array<String>,
            referenceRoles: Array<String>,
            referenceTargets: Array<CPNodeRef>,
        ): CPNode {
            checkForDuplicates(childrenIds)
            require(propertyRoles.size == propertyValues.size) { propertyRoles.size.toString() + " != " + propertyValues.size }
            require(referenceRoles.size == referenceTargets.size) { referenceRoles.size.toString() + " != " + referenceTargets.size }
            return CPNode(
                id,
                concept,
                parentId,
                roleInParent,
                childrenIds,
                propertyRoles,
                propertyValues,
                referenceRoles,
                referenceTargets,
            )
        }

        private fun checkForDuplicates(values: LongArray) {
            val copy = values.copyOf()
            copy.sort()
            for (i in 1 until copy.size) {
                if (copy[i - 1] == copy[i]) {
                    throw RuntimeException("Duplicate value: " + copy[i])
                }
            }
        }

        @JvmStatic
        fun deserialize(input: String): CPNode {
            return try {
                val parts = input.split(Separators.LEVEL1)
                val properties = parts[5].split(Separators.LEVEL2)
                    .filter { it.isNotEmpty() }
                    .map { it.split("=") }
                val references = parts[6].split(Separators.LEVEL2)
                    .filter { it.isNotEmpty() }
                    .map { it.split("=") }
                val propertyRoles = properties.map { unescape(it[0])!! }
                val propertyValues = properties.map { unescape(it[1]) }
                val propertiesWithoutNull = propertyRoles.zip(propertyValues).filter { it.second != null }
                val data = CPNode(
                    longFromHex(parts[0]),
                    unescape(parts[1]),
                    longFromHex(parts[2]),
                    unescape(parts[3]),
                    parts[4].split(Separators.LEVEL2).filter { it.isNotEmpty() }.map { longFromHex(it) }.toLongArray(),
                    propertiesWithoutNull.map { it.first }.toTypedArray(),
                    propertiesWithoutNull.map { it.second!! }.toTypedArray(),
                    references.map { unescape(it[0])!! }.toTypedArray(),
                    references.map { fromString(unescape(it[1])!!) }.toTypedArray(),
                )
                data.isWritten = true
                data
            } catch (ex: Exception) {
                throw RuntimeException("Failed to deserialize $input", ex)
            }
        }
    }
}
