package org.modelix.model.persistent

import org.modelix.model.lazy.CLVersion.Companion.INLINED_OPS_LIMIT
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.IObjectDeserializer
import org.modelix.model.objects.IObjectReferenceFactory
import org.modelix.model.objects.ObjectReference
import org.modelix.model.objects.getHashString
import org.modelix.model.operations.IOperation
import org.modelix.model.persistent.SerializationUtil.escape
import org.modelix.model.persistent.SerializationUtil.longFromHex
import org.modelix.model.persistent.SerializationUtil.longToHex
import org.modelix.model.persistent.SerializationUtil.unescape

data class CPVersion(
    val id: Long,
    val time: String?,
    val author: String?,

    val treeRef: ObjectReference<CPTree>,
    val previousVersion: ObjectReference<CPVersion>?,

    /**
     * The version created by the original author before it was rewritten during a merge
     */
    @Deprecated("A merge doesn't replace the version anymore, but stores references to the two merged versions")
    val originalVersion: ObjectReference<CPVersion>?,

    val baseVersion: ObjectReference<CPVersion>?,
    val mergedVersion1: ObjectReference<CPVersion>?,
    val mergedVersion2: ObjectReference<CPVersion>?,

    val operations: List<IOperation>?,
    val operationsHash: ObjectReference<OperationsList>?,
    val numberOfOperations: Int,
) : IObjectData {

    init {
        requireNotNull(treeRef) { "No tree hash provided" }
        if ((operations == null) == (operationsHash == null)) {
            throw RuntimeException("Only one of 'operations' and 'operationsHash' can be provided")
        }
        if (previousVersion != null && baseVersion != null) {
            throw RuntimeException("Only one of 'previousVersion' and 'baseVersion' can be provided")
        }
        if ((mergedVersion1 == null) != (mergedVersion2 == null)) {
            throw RuntimeException("A merge has to specify two versions. Only one was provided.")
        }
    }

    override fun serialize(): String {
        val opsPart: String = operationsHash?.getHash()?.toString()
            ?: if (operations!!.isEmpty()) {
                ""
            } else {
                operations.joinToString(Separators.OPS) { OperationSerializer.INSTANCE.serialize(it) }
            }
        val s = Separators.LEVEL1
        return longToHex(id) +
            s + escape(time) +
            s + escape(author) +
            s + nullAsEmptyString(treeRef.getHashString()) +
            s + nullAsEmptyString(baseVersion?.getHashString()) +
            s + nullAsEmptyString(mergedVersion1?.getHashString()) +
            s + nullAsEmptyString(mergedVersion2?.getHashString()) +
            s + numberOfOperations +
            s + opsPart
    }

    override fun getContainmentReferences(): List<ObjectReference<out IObjectData>> {
        return listOfNotNull(
            treeRef,
            previousVersion,
            originalVersion,
            baseVersion,
            mergedVersion1,
            mergedVersion2,
            operationsHash,
        )
    }

    override fun getNonContainmentReferences(): List<ObjectReference<IObjectData>> {
        return operations?.flatMap { it.getObjectReferences() } ?: emptyList()
    }

    override fun getDeserializer() = DESERIALIZER

    companion object : IObjectDeserializer<CPVersion> {
        val DESERIALIZER: IObjectDeserializer<CPVersion> = this

        override fun deserialize(
            serialized: String,
            referenceFactory: IObjectReferenceFactory,
        ): CPVersion {
            try {
                val parts = serialized.split(Separators.LEVEL1).toTypedArray()
                if (parts.size == 9) {
                    var opsHash: String? = null
                    var ops: List<IOperation>? = null
                    if (HashUtil.isSha256(parts[8])) {
                        opsHash = parts[8]
                    } else {
                        ops = parts[8].split(Separators.LEVEL2)
                            .filter { cs -> cs.isNotEmpty() }
                            .map { OperationSerializer.INSTANCE.deserialize(it, referenceFactory) }
                    }
                    val data = CPVersion(
                        longFromHex(parts[0]),
                        unescape(parts[1]),
                        unescape(parts[2]),
                        treeRef = referenceFactory.fromHashString(
                            checkNotNull(emptyStringAsNull(parts[3])) { "Tree hash empty in $serialized" },
                            CPTree.DESERIALIZER,
                        ),
                        previousVersion = null,
                        originalVersion = null,
                        baseVersion = emptyStringAsNull(parts[4])?.let { referenceFactory(it, CPVersion) },
                        mergedVersion1 = emptyStringAsNull(parts[5])?.let { referenceFactory(it, CPVersion) },
                        mergedVersion2 = emptyStringAsNull(parts[6])?.let { referenceFactory(it, CPVersion) },
                        operations = ops,
                        operationsHash = opsHash?.let { referenceFactory(it, OperationsList.DESERIALIZER) },
                        numberOfOperations = parts[7].toInt(),
                    )
                    return data
                } else {
                    var opsHash: String? = null
                    var ops: List<IOperation>? = null
                    if (HashUtil.isSha256(parts[5])) {
                        opsHash = parts[5]
                    } else {
                        ops = parts[5].split(Separators.LEVEL2)
                            .filter { cs: String? -> !cs.isNullOrEmpty() }
                            .map { serialized: String ->
                                OperationSerializer.INSTANCE.deserialize(
                                    serialized,
                                    referenceFactory,
                                )
                            }
                    }
                    val numOps = if (parts.size > 6) parts[6].toInt() else -1
                    val data = CPVersion(
                        id = longFromHex(parts[0]),
                        time = unescape(parts[1]),
                        author = unescape(parts[2]),
                        treeRef = referenceFactory(
                            checkNotNull(emptyStringAsNull(parts[3])) { "Tree hash empty in $serialized" },
                            CPTree.DESERIALIZER,
                        ),
                        previousVersion = emptyStringAsNull(parts[4])?.let { referenceFactory(it, DESERIALIZER) },
                        originalVersion = if (parts.size > 7) {
                            emptyStringAsNull(parts[7])?.let { referenceFactory(it, DESERIALIZER) }
                        } else {
                            null
                        },
                        baseVersion = null,
                        mergedVersion1 = null,
                        mergedVersion2 = null,
                        ops,
                        opsHash?.let { referenceFactory(it, OperationsList.DESERIALIZER) },
                        numOps,
                    )
                    return data
                }
            } catch (ex: Exception) {
                throw RuntimeException("Failed to deserialize version: $serialized", ex)
            }
        }
    }

    fun withOperations(operations: List<IOperation>, referenceFactory: IObjectReferenceFactory): CPVersion {
        val localizedOps = operations
        return if (localizedOps.size <= INLINED_OPS_LIMIT) {
            copy(
                operations = localizedOps,
                operationsHash = null,
                numberOfOperations = localizedOps.size,
            )
        } else {
            val opsList = OperationsList.of(localizedOps, referenceFactory)
            copy(
                operations = null,
                operationsHash = referenceFactory(opsList),
                numberOfOperations = localizedOps.size,
            )
        }
    }

    fun withUnloadedHistory(): CPVersion {
        return copy(
            baseVersion = baseVersion?.asUnloaded(),
            mergedVersion1 = mergedVersion1?.asUnloaded(),
            mergedVersion2 = mergedVersion2?.asUnloaded(),
            previousVersion = previousVersion?.asUnloaded(),
            originalVersion = originalVersion?.asUnloaded(),
        )
    }
}
