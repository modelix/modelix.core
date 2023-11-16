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

import org.modelix.model.LinearHistory
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
import org.modelix.model.operations.UndoOp
import org.modelix.model.persistent.MapBaseStore
import kotlin.random.Random
import kotlin.test.Test

// Suppress KLint error for underscores in variable names.
// `version_0_1` more readable in the test.
@Suppress("PropertyName")
class UndoTest {

    @Test
    fun undo_random() {
        val idGenerator = IdGenerator.newInstance(7)
        val versionIdGenerator = IdGenerator.newInstance(0)
        val store = ObjectStoreCache(MapBaseStore())
        val merger = VersionMerger(store, idGenerator)
        val baseBranch = OTBranch(PBranch(CLTree(store), idGenerator), idGenerator, store)
        val rand = Random(347663)

        randomChanges(baseBranch, 5, idGenerator, rand)
        val baseVersion = createVersion(baseBranch.operationsAndTree, null, versionIdGenerator, store)

        val maxIndex = 2
        val branches = (0..maxIndex).map { OTBranch(PBranch(baseVersion.tree, idGenerator), idGenerator, store) }.toList()
        for (i in 0..maxIndex) {
            branches[i].runWrite {
                randomChanges(branches[i], 5, idGenerator, rand)
            }
        }
        val versions = branches.map { branch ->
            createVersion(branch.operationsAndTree, baseVersion, versionIdGenerator, store)
        }.toList()

        val mergedVersions = ArrayList(versions)
        val version_0_1 = merger.mergeChange(mergedVersions[0], mergedVersions[1])
        val version_2_1 = merger.mergeChange(mergedVersions[2], mergedVersions[1])

        val version_1_1u = undo(versions[1], versionIdGenerator)
        version_1_1u.tree.visitChanges(baseVersion.tree, FailingVisitor())

        val version_0_1_1u = merger.mergeChange(version_0_1, version_1_1u)
        val version_2_1_1u = merger.mergeChange(version_2_1, version_1_1u)
        printHistory(version_1_1u, store)
        println("---")
        printHistory(versions[2], store)
        println("---")
        printHistory(version_2_1_1u, store)
        version_0_1_1u.tree.visitChanges(versions[0].tree, FailingVisitor())
        version_2_1_1u.tree.visitChanges(versions[2].tree, FailingVisitor())
    }

    @Test
    fun redo_random() {
        val idGenerator = IdGenerator.newInstance(7)
        val versionIdGenerator = IdGenerator.newInstance(0)
        val store = ObjectStoreCache(MapBaseStore())
        val merger = VersionMerger(store, idGenerator)
        val baseBranch = OTBranch(PBranch(CLTree(store), idGenerator), idGenerator, store)
        val rand = Random(347663)

        randomChanges(baseBranch, 5, idGenerator, rand)
        val baseVersion = createVersion(baseBranch.operationsAndTree, null, versionIdGenerator, store)

        val maxIndex = 2
        val branches = (0..maxIndex).map { OTBranch(PBranch(baseVersion.tree, idGenerator), idGenerator, store) }.toList()
        for (i in 0..maxIndex) {
            branches[i].runWrite {
                randomChanges(branches[i], 5, idGenerator, rand)
            }
        }
        val versions = branches.map { branch ->
            createVersion(branch.operationsAndTree, baseVersion, versionIdGenerator, store)
        }.toList()

        val mergedVersions = ArrayList(versions)
        val version_0_1 = merger.mergeChange(mergedVersions[0], mergedVersions[1])
        val version_2_1 = merger.mergeChange(mergedVersions[2], mergedVersions[1])

        val version_1_1u = undo(versions[1], versionIdGenerator)
        version_1_1u.tree.visitChanges(baseVersion.tree, FailingVisitor())
        val version_1_1u_1r = undo(version_1_1u, versionIdGenerator)
        version_1_1u_1r.tree.visitChanges(versions[1].tree, FailingVisitor())

        val version_0_1_1u_1r = merger.mergeChange(version_0_1, version_1_1u_1r)
        val version_2_1_1u_1r = merger.mergeChange(version_2_1, version_1_1u_1r)
        version_0_1_1u_1r.tree.visitChanges(version_0_1.tree, FailingVisitor())
        version_2_1_1u_1r.tree.visitChanges(version_2_1.tree, FailingVisitor())

        printHistory(version_0_1_1u_1r, store)
    }

    fun printHistory(version: CLVersion, store: IDeserializingKeyValueStore) {
        LinearHistory(null).load(version).forEach {
            println("Version ${it.id.toString(16)} ${it.hash} ${it.author}")
            for (op in it.operations) {
                println("    $op")
            }
        }
    }

    fun undo(version: CLVersion, idGenerator: IIdGenerator): CLVersion {
        return CLVersion.createRegularVersion(
            id = idGenerator.generate(),
            time = null,
            author = "undo",
            tree = version.baseVersion!!.tree,
            baseVersion = version,
            operations = arrayOf(UndoOp(KVEntryReference(version.data!!))),
        )
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
