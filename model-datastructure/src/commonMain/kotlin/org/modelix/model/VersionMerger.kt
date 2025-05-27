package org.modelix.model

import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectHash
import org.modelix.model.api.IIdGenerator
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.mutable.asMutableSingleThreaded
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.IOperationIntend
import org.modelix.model.operations.UndoOp

class VersionMerger
@Deprecated("idGenerator isn't required anymore")
constructor(private val idGenerator: IIdGenerator?) {
    constructor() : this(null)

    @Deprecated("store isn't required anymore")
    constructor(store: IDeserializingKeyValueStore, idGenerator: IIdGenerator) :
        this(idGenerator)

    @Deprecated("store isn't required anymore")
    constructor(store: IAsyncObjectStore, idGenerator: IIdGenerator) :
        this(idGenerator)

    private val logger = mu.KotlinLogging.logger {}
    fun mergeChange(lastMergedVersion: CLVersion, newVersion: CLVersion): CLVersion {
        require(lastMergedVersion.graph == newVersion.graph) {
            "Versions are part of different object graphs: $lastMergedVersion, $newVersion"
        }
        if (newVersion.getObjectHash() == lastMergedVersion.getObjectHash()) {
            return lastMergedVersion
        }
        checkRepositoryIds(lastMergedVersion, newVersion)
        val merged = mergeHistory(lastMergedVersion, newVersion)
        return merged
    }

    fun checkRepositoryIds(v1: CLVersion, v2: CLVersion) {
        val id1 = v1.tree.getId()
        val id2 = v2.tree.getId()
        if (id1 != id2) {
            throw RuntimeException("Tree ID mismatch: $id1 and $id2")
        }
    }

    private fun collectLatestNonMerges(version: CLVersion?, visited: MutableSet<ObjectHash>, result: MutableSet<ObjectHash>) {
        if (version == null) return
        if (!visited.add(version.getObjectHash())) return
        if (version.isMerge()) {
            collectLatestNonMerges(version.getMergedVersion1(), visited, result)
            collectLatestNonMerges(version.getMergedVersion2(), visited, result)
        } else {
            result.add(version.getObjectHash())
        }
    }

    protected fun mergeHistory(leftVersion: CLVersion, rightVersion: CLVersion): CLVersion {
        if (leftVersion.getObjectHash() == rightVersion.getObjectHash()) return leftVersion
        val commonBase = requireNotNull(commonBaseVersion(leftVersion, rightVersion)) {
            "Cannot merge versions without a common base: $leftVersion, $rightVersion"
        }
        if (commonBase.getContentHash() == leftVersion.getContentHash()) return rightVersion
        if (commonBase.getContentHash() == rightVersion.getContentHash()) return leftVersion

        val leftNonMerges = HashSet<ObjectHash>().also { collectLatestNonMerges(leftVersion, HashSet(), it) }
        val rightNonMerges = HashSet<ObjectHash>().also { collectLatestNonMerges(rightVersion, HashSet(), it) }
        if (leftNonMerges == rightNonMerges) {
            // If there is no actual change on both sides, but they just did the same merge, we have to pick one
            // of them, otherwise both sides will continue creating merges forever.
            return if (leftVersion.getObjectHash() < rightVersion.getObjectHash()) leftVersion else rightVersion
        }

        val versionsToApply = filterUndo(LinearHistory(commonBase.getContentHash()).load(leftVersion, rightVersion))

        val operationsToApply = versionsToApply.flatMap { captureIntend(it) }
        var mergedVersion: CLVersion? = null
        var baseTree = commonBase.getModelTree()
        val mutableTree = baseTree.asMutableSingleThreaded()
        mutableTree.runWrite {
            val appliedOps = operationsToApply.flatMap {
                val transformed: List<IOperation>
                try {
                    transformed = it.restoreIntend(mutableTree.getTransaction().tree)
                    logger.trace {
                        if (transformed.size != 1 || transformed[0] != it.getOriginalOp()) {
                            "transformed: ${it.getOriginalOp()} --> $transformed"
                        } else {
                            ""
                        }
                    }
                } catch (ex: Exception) {
                    throw RuntimeException("Operation intend failed: ${it.getOriginalOp()}", ex)
                }
                transformed.map { o ->
                    try {
                        o.apply(mutableTree)
                    } catch (ex: Exception) {
                        throw RuntimeException("Operation failed: $o", ex)
                    }
                }
            }
            mergedVersion = CLVersion.builder()
                .also { if (idGenerator != null) it.id(idGenerator.generate()) }
                .tree(mutableTree.getTransaction().tree)
                .autoMerge(commonBase.obj.ref, leftVersion.obj.ref, rightVersion.obj.ref)
                .operations(appliedOps.map { it.getOriginalOp() })
                .buildLegacy()
        }
        if (mergedVersion == null) {
            throw RuntimeException("Failed to merge ${leftVersion.getObjectHash()} and ${rightVersion.getObjectHash()}")
        }
        return mergedVersion
    }

    /**
     * Instead of computing and applying the inverse operation we can optimize for the case when an undo follows
     * directly after the version to be undone and just drop the version.
     */
    private fun filterUndo(versions: List<CLVersion>): List<CLVersion> {
        val filtered = versions.toMutableList()
        for (i in (0 until filtered.lastIndex).reversed()) {
            val v0 = filtered[i]
            val v1 = filtered.getOrNull(i + 1) ?: continue

            if (v1.numberOfOperations == 1 && (v1.operations.singleOrNull() as? UndoOp)?.versionHash?.getHash() == v0.getObjectHash()) {
                filtered.removeAt(i)
                filtered.removeAt(i)
            }
        }
        return filtered
    }

    private fun captureIntend(version: CLVersion): List<IOperationIntend> {
        val operations = version.operations.toList()
        if (operations.isEmpty()) return listOf()
        val baseVersion = version.baseVersion
            ?: throw RuntimeException("Version ${version.getObjectHash()} has operations but no baseVersion")
        val tree = baseVersion.getModelTree()
        val mutableTree = tree.asMutableSingleThreaded()
        return mutableTree.runWrite {
            operations.map {
                val intend = it.captureIntend(mutableTree.getTransaction().tree)
                it.apply(mutableTree)
                intend
            }
        }
    }

    companion object {
        fun commonBaseVersion(leftVersion: CLVersion?, rightVersion: CLVersion?): CLVersion? {
            if (leftVersion == null) return null
            if (rightVersion == null) return null
            return leftVersion.commonBaseVersion(rightVersion)
        }
    }
}

/**
 * Should only be used for conflict resolution to find out which operations need to be applied to which versions.
 * There is no guarantee that the base version is part of both branches of a merge.
 */
private fun IVersion.commonBaseVersion(other: IVersion): CLVersion? {
    var leftVersion: CLVersion? = this as CLVersion?
    var rightVersion: CLVersion? = other as CLVersion?
    val leftVersions: MutableSet<ObjectHash> = HashSet()
    val rightVersions: MutableSet<ObjectHash> = HashSet()
    leftVersions.add(this.getObjectHash())
    rightVersions.add(other.getObjectHash())

    while (leftVersion != null || rightVersion != null) {
        val leftBaseRef = leftVersion?.obj?.data?.baseVersion
        val rightBaseRef = rightVersion?.obj?.data?.baseVersion
        leftBaseRef?.let { leftVersions.add(it.getHash()) }
        rightBaseRef?.let { rightVersions.add(it.getHash()) }

        if (leftVersion != null) {
            if (rightVersions.contains(leftVersion.getObjectHash())) {
                return leftVersion
            }
        }
        if (rightVersion != null) {
            if (leftVersions.contains(rightVersion.getObjectHash())) {
                return rightVersion
            }
        }

        val leftLoadedBase = leftBaseRef?.getLoadedData()
        val rightLoadedBase = rightBaseRef?.getLoadedData()

        if (leftLoadedBase != null || rightLoadedBase != null) {
            // As long as one of the versions is available without sending a query, follow that path. The probability
            // is high that the common base is found in there, and we don't have to send any queries at all.

            if (leftLoadedBase != null) {
                leftVersion = CLVersion(Object(leftLoadedBase, leftBaseRef))
            }
            if (rightLoadedBase != null) {
                rightVersion = CLVersion(Object(rightLoadedBase, rightBaseRef))
            }
        } else {
            if (leftVersion != null) {
                leftVersion = leftVersion.baseVersion
            }
            if (rightVersion != null) {
                rightVersion = rightVersion.baseVersion
            }
        }
    }
    return null
}
