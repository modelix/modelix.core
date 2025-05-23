package org.modelix.model.persistent

import org.modelix.datastructures.hamt.HamtNode
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.getHashString
import org.modelix.datastructures.patricia.PatriciaNode
import org.modelix.kotlin.utils.urlDecode
import org.modelix.kotlin.utils.urlEncode
import org.modelix.model.TreeType
import org.modelix.model.lazy.CLVersion.Companion.INLINED_OPS_LIMIT
import org.modelix.model.operations.IOperation
import org.modelix.model.persistent.SerializationUtil.escape
import org.modelix.model.persistent.SerializationUtil.longFromHex
import org.modelix.model.persistent.SerializationUtil.longToHex
import org.modelix.model.persistent.SerializationUtil.unescape
import org.modelix.streams.IStream

data class CPVersion(
    /**
     * This ID was used in earlier versions where merges were done by rebasing.
     * After supporting a non-linear history where the two merged versions are recorded and stay part of the history
     * with their original ObjectHash, this ID isn't necessary anymore.
     */
    @Deprecated("Use the ObjectHash instead")
    val id: Long,

    val time: String?,
    val author: String?,

    val treeRefs: Map<TreeType, ObjectReference<CPTree>>,
    val previousVersion: ObjectReference<CPVersion>?,

    /**
     * The version created by the original author before it was rewritten during a merge
     */
    @Deprecated("A merge doesn't replace the version anymore, but stores references to the two merged versions")
    val originalVersion: ObjectReference<CPVersion>?,

    /**
     * The base version for the list of operations. When you start with the [baseVersion] and apply the list of
     * operations in the correct order, you should get [this] version as the result.
     * In case of a merge commit, it can be one of the two merged version, their common base or any other version in the
     * history.
     */
    val baseVersion: ObjectReference<CPVersion>?,
    val mergedVersion1: ObjectReference<CPVersion>?,
    val mergedVersion2: ObjectReference<CPVersion>?,

    val operations: List<IOperation>?,
    val operationsHash: ObjectReference<OperationsList>?,
    val numberOfOperations: Int,

    /**
     * Additional information such as the Git commit ID that was imported.
     */
    val attributes: Map<String, String> = emptyMap(),
) : IObjectData {

    init {
        if (operations != null && operationsHash != null) {
            throw RuntimeException("Only one of 'operations' and 'operationsHash' can be provided")
        }
        if (previousVersion != null && baseVersion != null) {
            throw RuntimeException("Only one of 'previousVersion' and 'baseVersion' can be provided")
        }
        if ((mergedVersion1 == null) != (mergedVersion2 == null)) {
            throw RuntimeException("A merge has to specify two versions. Only one was provided.")
        }
        require(treeRefs.all { it.value.getDeserializer() == CPTree })
    }

    fun getTree(type: TreeType): ObjectReference<CPTree> {
        return checkNotNull(treeRefs[type]) { "Version $this doesn't contain a $type tree" }
    }

    override fun serialize(): String {
        val opsPart: String = operationsHash?.getHash()?.toString()
            ?: if (operations.isNullOrEmpty()) {
                ""
            } else {
                operations.joinToString(Separators.OPS) { OperationSerializer.INSTANCE.serialize(it) }
            }

        // The serialization format is chosen in a way that a single legacy tree serializes into a simple ObjectHash,
        // which makes it backwards compatible.
        val serializedTrees = treeRefs.entries.sortedBy { it.key.name }.joinToString(Separators.LEVEL2) {
            val treeImplPrefix = when (it.value.getDeserializer()) {
                CPTree -> ""
                is HamtNode.Deserializer<*, *> -> ""
                is PatriciaNode.Deserializer<*, *> -> {
                    // S as in [S]tring based node IDs
                    "S" + Separators.LEVEL4
                }
                else -> error("Unsupported tree implementation: ${it.value.getDeserializer()}")
            }
            val treeTypeAndHash = if (it.key == TreeType.MAIN) {
                it.value.getHashString()
            } else {
                it.key.name + Separators.MAPPING + it.value.getHashString()
            }
            treeImplPrefix + treeTypeAndHash
        }

        val s = Separators.LEVEL1
        val attributesPart = if (attributes.isEmpty()) {
            ""
        } else {
            s + attributes.entries.sortedBy { it.key }.joinToString(Separators.LEVEL2) {
                it.key.urlEncode() + Separators.MAPPING + it.value.urlEncode()
            }
        }

        return longToHex(id) +
            s + escape(time) +
            s + escape(author) +
            s + serializedTrees +
            s + nullAsEmptyString(baseVersion?.getHashString()) +
            s + nullAsEmptyString(mergedVersion1?.getHashString()) +
            s + nullAsEmptyString(mergedVersion2?.getHashString()) +
            s + numberOfOperations +
            s + opsPart +
            attributesPart
    }

    override fun getContainmentReferences(): List<ObjectReference<out IObjectData>> {
        return listOfNotNull(
            previousVersion,
            originalVersion,
            baseVersion,
            mergedVersion1,
            mergedVersion2,
            operationsHash,
        ) + treeRefs.values
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
                if (parts.size >= 9) {
                    // <id>/<time>/<author>/<tree>/<baseVersion>/<mergedVersion1>/<mergedVersion2>/<numOps>/<ops>[/attributes]
                    var opsHash: String? = null
                    var ops: List<IOperation>? = null
                    if (HashUtil.isSha256(parts[8])) {
                        opsHash = parts[8]
                    } else {
                        ops = parts[8].split(Separators.LEVEL2)
                            .filter { cs -> cs.isNotEmpty() }
                            .map { OperationSerializer.INSTANCE.deserialize(it, referenceFactory) }
                    }

                    val treeHashes: Map<TreeType, ObjectReference<CPTree>> = checkNotNull(emptyStringAsNull(parts[3])) { "Tree hash empty in $serialized" }
                        .split(Separators.LEVEL2)
                        .associate {
                            it.split(Separators.MAPPING).let {
                                when (it.size) {
                                    1 -> TreeType.MAIN to referenceFactory(it[0], CPTree)
                                    2 -> TreeType(it[0]) to referenceFactory(it[1], CPTree)
                                    else -> throw IllegalArgumentException("Invalid tree reference $it in $serialized")
                                }
                            }
                        }

                    val attributes = parts.getOrNull(9)?.let {
                        it.split(Separators.LEVEL2).mapNotNull {
                            val (key, value) = it.split(Separators.MAPPING)
                            (key.urlDecode() ?: return@mapNotNull null) to (value.urlDecode() ?: return@mapNotNull null)
                        }.toMap()
                    } ?: emptyMap()

                    val data = CPVersion(
                        id = longFromHex(parts[0]),
                        time = unescape(parts[1]),
                        author = unescape(parts[2]),
                        treeRefs = treeHashes,
                        previousVersion = null,
                        originalVersion = null,
                        baseVersion = emptyStringAsNull(parts[4])?.let { referenceFactory(it, CPVersion) },
                        mergedVersion1 = emptyStringAsNull(parts[5])?.let { referenceFactory(it, CPVersion) },
                        mergedVersion2 = emptyStringAsNull(parts[6])?.let { referenceFactory(it, CPVersion) },
                        operations = ops,
                        operationsHash = opsHash?.let { referenceFactory(it, OperationsList.DESERIALIZER) },
                        numberOfOperations = parts[7].toInt(),
                        attributes = attributes,
                    )
                    return data
                } else {
                    // legacy serialization format
                    // <id>/<time>/<author>/<tree>/<previousVersion>/<ops>/<numOps>/<originalVersion>

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
                        treeRefs = mapOf(TreeType.MAIN to referenceFactory(checkNotNull(emptyStringAsNull(parts[3])) { "Tree hash empty in $serialized" }, CPTree)),
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
        if (baseVersion?.isLoaded() != true &&
            mergedVersion1?.isLoaded() != true &&
            mergedVersion2?.isLoaded() != true &&
            previousVersion?.isLoaded() != true &&
            originalVersion?.isLoaded() != true
        ) {
            return this
        }
        return copy(
            baseVersion = baseVersion?.asUnloaded(),
            mergedVersion1 = mergedVersion1?.asUnloaded(),
            mergedVersion2 = mergedVersion2?.asUnloaded(),
            previousVersion = previousVersion?.asUnloaded(),
            originalVersion = originalVersion?.asUnloaded(),
        )
    }

    override fun objectDiff(self: Object<*>, oldObject: Object<*>?): IStream.Many<Object<*>> {
        throw UnsupportedOperationException("CLVersion.diff")
    }
}
