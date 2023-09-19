/*
 * Copyright (c) 2023.
 *
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

import org.modelix.model.VersionMerger
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.operations.IAppliedOperation
import org.modelix.model.operations.OTBranch
import org.modelix.model.operations.OTWriteTransaction
import org.modelix.model.operations.RevertToOp
import org.modelix.model.persistent.MapBaseStore
import kotlin.random.Random
import kotlin.test.Test

class RevertTest {

    @Test
    fun revert_random() {
        val idGenerator = IdGenerator.newInstance(7)
        val versionIdGenerator = IdGenerator.newInstance(0)
        val store = ObjectStoreCache(MapBaseStore())
        val baseBranch = OTBranch(PBranch(CLTree(store), idGenerator), idGenerator, store)
        val rand = Random(916306)

        val allVersions = mutableListOf<CLVersion>()

        randomChanges(baseBranch, 5, idGenerator, rand)

        allVersions += createVersion(baseBranch.operationsAndTree, null, versionIdGenerator, store)

        for (i in 0..100) {
            if (rand.nextInt(10) == 0) {
                val v1 = allVersions[rand.nextInt(allVersions.size)]
                val v2 = allVersions[rand.nextInt(allVersions.size)]
                allVersions += VersionMerger(store, versionIdGenerator).mergeChange(v1, v2)
            } else {
                val v1 = allVersions[rand.nextInt(allVersions.size)]
                val branch = OTBranch(PBranch(v1.tree, idGenerator), idGenerator, store)
                branch.runWrite {
                    randomChanges(branch, 5, idGenerator, rand)
                }
                allVersions += createVersion(branch.operationsAndTree, v1, versionIdGenerator, store)
            }
        }

        for (version in allVersions) {
            val history = collectAllVersionsFromHistory(version).toList().drop(1)
            if (history.isEmpty()) continue
            val revertTo = history[rand.nextInt(history.size)]
            val revertedVersion = revert(version, revertTo, versionIdGenerator)

            revertedVersion.tree.visitChanges(revertTo.tree, FailingVisitor())
        }
    }

    fun revert(latestKnownVersion: CLVersion, versionToRevertTo: CLVersion, idGenerator: IIdGenerator): CLVersion {
        val revertOp = RevertToOp(KVEntryReference(latestKnownVersion.data!!), KVEntryReference(versionToRevertTo.data!!))
        val branch = OTBranch(PBranch(latestKnownVersion.tree, idGenerator), idGenerator, latestKnownVersion.store)
        branch.runWriteT { t ->
            (t as OTWriteTransaction).apply(revertOp)
        }

        val (ops, tree) = branch.operationsAndTree
        return CLVersion.createRegularVersion(
            id = idGenerator.generate(),
            time = null,
            author = "revert",
            tree = tree as CLTree,
            baseVersion = latestKnownVersion,
            operations = ops.map { it.getOriginalOp() }.toTypedArray(),
        )
    }

    fun collectAllVersionsFromHistory(version: CLVersion, allVersions: MutableSet<CLVersion> = LinkedHashSet()): Set<CLVersion> {
        if (allVersions.contains(version)) return allVersions
        allVersions.add(version)
        version.getMergedVersion1()?.let { collectAllVersionsFromHistory(it, allVersions) }
        version.getMergedVersion2()?.let { collectAllVersionsFromHistory(it, allVersions) }
        version.baseVersion?.let { collectAllVersionsFromHistory(it, allVersions) }
        return allVersions
    }

    private fun randomChanges(baseBranch: OTBranch, numChanges: Int, idGenerator: IIdGenerator, rand: Random) {
        baseBranch.runWrite {
            val changeGenerator = RandomTreeChangeGenerator(idGenerator, rand).growingOperationsOnly()
            for (i in 0 until numChanges) {
                changeGenerator.applyRandomChange(baseBranch, null)
            }
        }
    }

    fun createVersion(
        opsAndTree: Pair<List<IAppliedOperation>, ITree>,
        previousVersion: CLVersion?,
        idGenerator: IIdGenerator,
        storeCache: IDeserializingKeyValueStore,
    ): CLVersion {
        return CLVersion.createRegularVersion(
            id = idGenerator.generate(),
            time = null,
            author = null,
            tree = opsAndTree.second as CLTree,
            baseVersion = previousVersion,
            operations = opsAndTree.first.map { it.getOriginalOp() }.toTypedArray(),
        )
    }
}
