package org.modelix.model

import org.modelix.model.api.IBranch
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.TreePointer
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.commonBaseVersion
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.IOperationIntend
import org.modelix.model.operations.UndoOp

class VersionMerger(private val idGenerator: IIdGenerator) {
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
        if (newVersion.hash == lastMergedVersion.hash) {
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

    private fun collectLatestNonMerges(version: CLVersion?, visited: MutableSet<String>, result: MutableSet<Long>) {
        if (version == null) return
        if (!visited.add(version.getContentHash())) return
        if (version.isMerge()) {
            collectLatestNonMerges(version.getMergedVersion1(), visited, result)
            collectLatestNonMerges(version.getMergedVersion2(), visited, result)
        } else {
            result.add(version.id)
        }
    }

    protected fun mergeHistory(leftVersion: CLVersion, rightVersion: CLVersion): CLVersion {
        if (leftVersion.hash == rightVersion.hash) return leftVersion
        val commonBase = requireNotNull(commonBaseVersion(leftVersion, rightVersion)) {
            "Cannot merge versions without a common base: $leftVersion, $rightVersion"
        }
        if (commonBase.getContentHash() == leftVersion.getContentHash()) return rightVersion
        if (commonBase.getContentHash() == rightVersion.getContentHash()) return leftVersion

        val leftNonMerges = HashSet<Long>().also { collectLatestNonMerges(leftVersion, HashSet(), it) }
        val rightNonMerges = HashSet<Long>().also { collectLatestNonMerges(rightVersion, HashSet(), it) }
        if (leftNonMerges == rightNonMerges) {
            // If there is no actual change on both sides, but they just did the same merge, we have to pick one
            // of them, otherwise both sides will continue creating merges forever.
            return if (leftVersion.id < rightVersion.id) leftVersion else rightVersion
        }

        val versionsToApply = filterUndo(LinearHistory(commonBase.getContentHash()).load(leftVersion, rightVersion))

        val operationsToApply = versionsToApply.flatMap { captureIntend(it) }
        var mergedVersion: CLVersion? = null
        var baseTree = commonBase.getTree()
        val branch: IBranch = TreePointer(baseTree)
        branch.runWrite {
            val t = branch.writeTransaction
            val appliedOps = operationsToApply.flatMap {
                val transformed: List<IOperation>
                try {
                    transformed = it.restoreIntend(t.tree)
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
                        o.apply(t)
                    } catch (ex: Exception) {
                        throw RuntimeException("Operation failed: $o", ex)
                    }
                }
            }
            mergedVersion = CLVersion.createAutoMerge(
                idGenerator.generate(),
                t.tree,
                commonBase,
                leftVersion,
                rightVersion,
                appliedOps.map { it.getOriginalOp() }.toTypedArray(),
            )
        }
        if (mergedVersion == null) {
            throw RuntimeException("Failed to merge ${leftVersion.hash} and ${rightVersion.hash}")
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

            if (v1.numberOfOperations == 1 && (v1.operations.single() as? UndoOp)?.versionHash?.getHash() == v0.getObjectHash()) {
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
            ?: throw RuntimeException("Version ${version.hash} has operations but no baseVersion")
        val tree = baseVersion.tree
        val branch = TreePointer(tree)
        return branch.computeWrite {
            operations.map {
                val intend = it.captureIntend(branch.transaction.tree)
                it.apply(branch.writeTransaction)
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
