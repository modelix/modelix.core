package org.modelix.model.persistent

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.model.lazy.COWArrays.copy
import org.modelix.model.lazy.COWArrays.insert
import org.modelix.model.lazy.COWArrays.remove
import org.modelix.model.lazy.COWArrays.removeAt
import org.modelix.model.lazy.COWArrays.set
import org.modelix.model.persistent.CPNodeRef.Companion.fromString
import org.modelix.model.persistent.SerializationUtil.escape
import org.modelix.model.persistent.SerializationUtil.longFromHex
import org.modelix.model.persistent.SerializationUtil.longToHex
import org.modelix.model.persistent.SerializationUtil.unescape
import kotlin.jvm.JvmStatic

class CPNode(
    val id: Long,
    val concept: String?,
    val parentId: Long,
    val roleInParent: String?,
    private val childrenIds: LongArray,
    val propertyRoles: Array<String>,
    val propertyValues: Array<String>,
    val referenceRoles: Array<String>,
    val referenceTargets: Array<CPNodeRef>,
) : IObjectData {

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

    fun withConcept(newValue: String?): CPNode {
        if (concept == newValue) return this
        return create(
            id,
            newValue,
            parentId,
            roleInParent,
            childrenIds,
            propertyRoles,
            propertyValues,
            referenceRoles,
            referenceTargets,
        )
    }

    fun withContainment(newParent: Long, newRole: String?): CPNode {
        if (newRole == roleInParent && parentId == newParent) return this
        return create(
            id,
            concept,
            newParent,
            newRole,
            childrenIds,
            propertyRoles,
            propertyValues,
            referenceRoles,
            referenceTargets,
        )
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

    fun withChildRemoved(childId: Long): CPNode {
        return create(
            id,
            concept,
            parentId,
            roleInParent,
            remove(childrenIdArray, childId),
            propertyRoles,
            propertyValues,
            referenceRoles,
            referenceTargets,
        )
    }

    override fun getDeserializer() = DESERIALIZER
    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> = emptyList()

    companion object : IObjectDeserializer<CPNode> {
        val DESERIALIZER: IObjectDeserializer<CPNode> = this

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
                    throw RuntimeException("Duplicate value " + copy[i].toString(16) + "  in " + values.map { it.toString(16) })
                }
            }
        }

        override fun deserialize(input: String, referenceFactory: IObjectReferenceFactory): CPNode {
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
                data
            } catch (ex: Exception) {
                throw RuntimeException("Failed to deserialize $input", ex)
            }
        }
    }
}
