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

package org.modelix.model

import org.modelix.model.api.IBranch
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.ITree
import org.modelix.model.api.TreePointer
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.IOperationIntend
import org.modelix.model.operations.UndoOp
import org.modelix.model.persistent.CPVersion

class VersionMerger(private val storeCache: IDeserializingKeyValueStore, private val idGenerator: IIdGenerator) {
    private val logger = mu.KotlinLogging.logger {}
    fun mergeChange(lastMergedVersion: CLVersion, newVersion: CLVersion): CLVersion {
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
        val commonBase = Companion.commonBaseVersion(leftVersion, rightVersion)
        if (commonBase?.hash == leftVersion.hash) return rightVersion
        if (commonBase?.hash == rightVersion.hash) return leftVersion

        val leftNonMerges = HashSet<Long>().also { collectLatestNonMerges(leftVersion, HashSet(), it) }
        val rightNonMerges = HashSet<Long>().also { collectLatestNonMerges(rightVersion, HashSet(), it) }
        if (leftNonMerges == rightNonMerges) {
            // If there is no actual change on both sides, but they just did the same merge, we have to pick one
            // of them, otherwise both sides will continue creating merges forever.
            return if (leftVersion.id < rightVersion.id) leftVersion else rightVersion
        }

        val versionsToApply = filterUndo(LinearHistory(commonBase?.hash).load(leftVersion, rightVersion))

        val operationsToApply = versionsToApply.flatMap { captureIntend(it) }
        var mergedVersion: CLVersion? = null
        var baseTree = commonBase?.tree ?: CLTree(storeCache)
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
                        o.apply(t, (t.tree as CLTree).store)
                    } catch (ex: Exception) {
                        throw RuntimeException("Operation failed: $o", ex)
                    }
                }
            }
            mergedVersion = CLVersion.createAutoMerge(
                idGenerator.generate(),
                t.tree as CLTree,
                commonBase!!,
                leftVersion,
                rightVersion,
                appliedOps.map { it.getOriginalOp() }.toTypedArray(),
                storeCache,
            )
        }
        if (mergedVersion == null) {
            throw RuntimeException("Failed to merge ${leftVersion.hash} and ${rightVersion.hash}")
        }
        return mergedVersion!!
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

            if (v1.numberOfOperations == 1 && (v1.operations.single() as? UndoOp)?.versionHash?.getHash() == v0.getContentHash()) {
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
                val intend = it.captureIntend(branch.transaction.tree, storeCache)
                it.apply(branch.writeTransaction, storeCache)
                intend
            }
        }
    }

    private fun getVersion(hash: String): CLVersion {
        return CLVersion.loadFromHash(hash, storeCache)
    }

    protected fun getTree(version: CPVersion): ITree {
        return CLTree(version.treeHash!!.getValue(storeCache), storeCache)
    }

    companion object {
        fun commonBaseVersion(leftVersion: CLVersion?, rightVersion: CLVersion?): CLVersion? {
            var leftVersion = leftVersion
            var rightVersion = rightVersion
            val leftVersions: MutableSet<String> = HashSet()
            val rightVersions: MutableSet<String> = HashSet()
            while (leftVersion != null || rightVersion != null) {
                if (leftVersion != null) {
                    leftVersions.add(leftVersion.hash)
                }
                if (rightVersion != null) {
                    rightVersions.add(rightVersion.hash)
                }
                if (leftVersion != null) {
                    if (rightVersions.contains(leftVersion.hash)) {
                        return leftVersion
                    }
                }
                if (rightVersion != null) {
                    if (leftVersions.contains(rightVersion.hash)) {
                        return rightVersion
                    }
                }
                if (leftVersion != null) {
                    leftVersion = leftVersion.baseVersion
                }
                if (rightVersion != null) {
                    rightVersion = rightVersion.baseVersion
                }
            }
            return null
        }
    }
}
