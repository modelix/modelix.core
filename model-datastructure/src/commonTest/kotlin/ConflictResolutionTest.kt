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
import org.modelix.model.api.IConcept
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.PBranch
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.operations.IAppliedOperation
import org.modelix.model.operations.OTBranch
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

class ConflictResolutionTest : TreeTestBase() {
    private val logger = mu.KotlinLogging.logger {}

    @Test
    fun randomTest2Branches() {
        for (i in 1000..1100) {
            try {
                rand = Random(i)
                randomTest(5, 2, 3)
            } catch (ex: Exception) {
                throw RuntimeException("Failed for seed $i", ex)
            }
        }
    }

    @Test
    fun randomTest5Branches() {
        for (i in 10000..10010) {
            try {
                rand = Random(i)
                randomTest(5, 5, 5)
            } catch (ex: Exception) {
                throw RuntimeException("Failed for seed $i", ex)
            }
        }
    }

    fun randomTest(baseChanges: Int, numBranches: Int, branchChanges: Int) {
        val merger = VersionMerger(storeCache, idGenerator)
        val baseBranch = OTBranch(PBranch(initialTree, idGenerator), idGenerator, storeCache)
        logger.trace { "Random changes to base" }
        for (i in 0 until baseChanges) {
            RandomTreeChangeGenerator(idGenerator, rand)
                .addOperationOnly()
                .applyRandomChange(baseBranch, null)
        }
        val baseVersion = createVersion(baseBranch.operationsAndTree, null)

        val maxIndex = numBranches - 1
        val branches = (0..maxIndex).map { OTBranch(PBranch(baseVersion.tree, idGenerator), idGenerator, storeCache) }.toList()
        val versions = branches.mapIndexed { index, branch ->
            logger.trace { "Random changes to branch $index" }
            for (i in 0 until branchChanges) {
                applyRandomChange(branch, null)
            }
            createVersion(branch.operationsAndTree, baseVersion)
        }.toList()
        val mergedVersions = ArrayList(versions)

        for (i in 0..maxIndex) for (i2 in 0..maxIndex) {
            if (i == i2) continue
            logger.trace { "Merge branch $i2 into $i" }
            mergedVersions[i] = merger.mergeChange(mergedVersions[i], mergedVersions[i2])
        }

        for (i in 1..maxIndex) {
            assertSameTree(mergedVersions[0].tree, mergedVersions[i].tree)
        }
    }

    fun knownIssueTest(baseChanges: (IWriteTransaction) -> Unit, vararg branchChanges: (IWriteTransaction) -> Unit) {
        val merger = VersionMerger(storeCache, idGenerator)
        val baseBranch = OTBranch(PBranch(initialTree, idGenerator), idGenerator, storeCache)

        baseBranch.runWrite {
            logger.trace { "Changes to base branch" }
            baseChanges(baseBranch.writeTransaction)
        }

        val baseVersion = createVersion(baseBranch.operationsAndTree, null)

        val maxIndex = branchChanges.size - 1
        val branches = (0..maxIndex).map { OTBranch(PBranch(baseVersion.tree, idGenerator), idGenerator, storeCache) }.toList()
        for (i in 0..maxIndex) {
            branches[i].runWrite {
                logger.trace { "Changes to branch $i" }
                branchChanges[i](branches[i].writeTransaction)
            }
        }
        val versions = branches.map { branch ->
            createVersion(branch.operationsAndTree, baseVersion)
        }.toList()

        val mergedVersions = ArrayList(versions)

        for (i in 0..maxIndex) for (i2 in 0..maxIndex) {
            if (i == i2) continue
            logger.trace { "Merge branch $i2 into $i" }
            mergedVersions[i] = merger.mergeChange(mergedVersions[i], mergedVersions[i2])
        }

        for (i in 1..maxIndex) {
            assertSameTree(mergedVersions[0].tree, mergedVersions[i].tree)
        }
    }

    @Test
    fun knownIssue01() {
        knownIssueTest(
            { t ->
                t.addNewChild(ITree.ROOT_ID, "role1", 0, 0xe, null as IConcept?)
                t.addNewChild(ITree.ROOT_ID, "role2", 0, 0x12, null as IConcept?)
            },
            { t ->
                t.moveChild(ITree.ROOT_ID, "role3", 0, 0xe)
                t.deleteNode(0xe)
            },
            { t ->
                t.moveChild(ITree.ROOT_ID, "role1", 1, 0x12)
                t.deleteNode(0xe)
                t.deleteNode(0x12)
            },
        )
    }

    @Test
    fun knownIssue02() {
        knownIssueTest(
            { t ->
                t.addNewChild(ITree.ROOT_ID, "role2", 0, 0x3, null as IConcept?)
            },
            { t ->
                t.deleteNode(0x3)
            },
            { t ->
                t.deleteNode(0x3)
                t.addNewChild(ITree.ROOT_ID, "role2", 0, 0x13, null as IConcept?)
                t.deleteNode(0x13)
            },
        )
    }

    @Test
    fun knownIssue03() {
        knownIssueTest(
            { t ->
            },
            { t ->
                t.addNewChild(1, "role2", 0, 0xff00000007, null as IConcept?)
                t.deleteNode(0xff00000007)
            },
            { t ->
                t.addNewChild(1, "role2", 0, 0xff0000000a, null as IConcept?)
                t.deleteNode(0xff0000000a)
            },
            { t ->
                t.addNewChild(1, "role2", 0, 0xff0000000e, null as IConcept?)
                t.deleteNode(0xff0000000e)
            },
        )
    }

    @Test
    fun knownIssue04() {
        knownIssueTest(
            { t ->
            },
            { t ->
                t.addNewChild(1, "role3", 0, 0xff00000006, null as IConcept?)
                t.addNewChild(0xff00000006, "role3", 0, 0xff00000008, null as IConcept?)
                t.moveChild(1, "role1", 0, 0xff00000006)
            },
            { t ->
                t.addNewChild(1, "role3", 0, 0xff0000000e, null as IConcept?)
            },
        )
    }

    @Test
    fun knownIssue05() {
        knownIssueTest(
            { t ->
            },
            { t -> // 0
            },
            { t -> // 1
            },
            { t -> // 2
                t.addNewChild(0x1, "role3", 0, 0xff0000000f, null as IConcept?)
            },
            { t -> // 3
                t.addNewChild(0x1, "role3", 0, 0xff00000011, null as IConcept?)
                t.deleteNode(0xff00000011)
            },
        )
    }

    @Test
    fun knownIssue06() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role1", 0, 0xff0000000e, null as IConcept?)
                t.addNewChild(0x1, "role2", 0, 0xff00000011, null as IConcept?)
                t.addNewChild(0xff00000011, "role1", 0, 0xff00000010, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0x1, "role1", 0, 0xff00000010)
                t.deleteNode(0xff00000010)
            },
            { t -> // 1
                t.deleteNode(0xff0000000e)
                t.moveChild(0x1, "role1", 0, 0xff00000011)
                t.deleteNode(0xff00000010)
                t.moveChild(0x1, "role1", 0, 0xff00000011)
            },
        )
        // 1.role1[0] expected to be ff00000011, but was ff00000010
    }

    @Test
    fun knownIssue07() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role1", 0, 0xff0000000e, null as IConcept?)
                t.addNewChild(0xff0000000e, "role3", 0, 0xff00000010, null as IConcept?)
                t.addNewChild(0xff00000010, "role2", 0, 0xff00000011, null as IConcept?)
            },
            { t -> // 0
                t.deleteNode(0xff00000011)
            },
            { t -> // 1
                t.moveChild(0x1, "role2", 0, 0xff00000011)
                t.addNewChild(0x1, "role2", 0, 0xff00000032, null as IConcept?)
            },
        )
        // Attempt to access a deleted location: 1
    }

    @Test
    fun knownIssue08() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role1", 0, 0xff0000000e, null as IConcept?)
                t.addNewChild(0x1, "role5", 0, 0xff00000010, null as IConcept?)
                t.addNewChild(0xff00000010, "role2", 0, 0xff00000011, null as IConcept?)
                t.addNewChild(0xff00000010, "role1", 0, 0xff00000012, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0xff00000012, "role2", 0, 0xff0000000e)
            },
            { t -> // 1
                t.moveChild(0x1, "role1", 0, 0xff00000011)
            },
        )
        // Attempt to access a deleted location: 0
    }

    @Test
    fun knownIssue09a() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role1", 0, 0xff0000000e, null as IConcept?)
                t.addNewChild(0x1, "role2", 0, 0xff00000011, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0x1, "role6", 0, 0xff0000000e)
            },
            { t -> // 1
                t.moveChild(0x1, "role1", 0, 0xff00000011)
                t.deleteNode(0xff00000011)
            },
        )
    }

    @Test
    fun knownIssue09b() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role1", 0, 0xff0000000e, null as IConcept?)
                t.addNewChild(0x1, "role2", 0, 0xff00000011, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0x1, "role6", 0, 0xff0000000e)
            },
            { t -> // 1
                t.moveChild(0x1, "role1", 1, 0xff00000011)
                t.deleteNode(0xff00000011)
            },
        )
    }

    @Test
    fun knownIssue09c() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role1", 0, 0xff000000aa, null as IConcept?)
                t.addNewChild(0x1, "role1", 1, 0xff0000000e, null as IConcept?)
                t.addNewChild(0x1, "role2", 0, 0xff00000011, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0x1, "role6", 0, 0xff0000000e)
            },
            { t -> // 1
                t.moveChild(0x1, "role1", 0, 0xff00000011)
                t.deleteNode(0xff00000011)
            },
        )
    }

    @Test
    fun knownIssue10() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role1", 0, 0xff00000010, null as IConcept?)
            },
            { t -> // 0
                t.deleteNode(0xff00000010)
            },
            { t -> // 1
                t.addNewChild(0xff00000010, "role1", 0, 0xff0000002b, null as IConcept?)
                t.deleteNode(0xff0000002b)
            },
        )
    }

    @Test
    fun knownIssue11() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role2", 0, 0xff00000011, null as IConcept?)
            },
            { t -> // 0
                t.deleteNode(0xff00000011)
            },
            { t -> // 1
                t.addNewChild(0xff00000011, "role2", 0, 0xff0000002e, null as IConcept?)
                t.moveChild(0x1, "role3", 0, 0xff0000002e)
            },
            { t -> // 2
                t.addNewChild(0xff00000011, "role3", 0, 0xff00000043, null as IConcept?)
            },
        )
    }

    @Test
    fun knownIssue12() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role1", 0, 0xff00000012, null as IConcept?)
                t.addNewChild(0x1, "role3", 0, 0xff0000000e, null as IConcept?)
            },
            { t -> // 0
                t.deleteNode(0xff00000012)
                t.deleteNode(0xff0000000e)
            },
            { t -> // 1
                t.addNewChild(0xff00000012, "role3", 0, 0xff00000043, null as IConcept?)
                t.addNewChild(0xff0000000e, "role3", 0, 0xff00000044, null as IConcept?)
                t.deleteNode(0xff00000043)
            },
        )
    }

    @Test
    fun knownIssue13() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role4", 0, 0xff00000012, null as IConcept?)
            },
            { t -> // 0
                t.deleteNode(0xff00000012)
            },
            { t -> // 1
                t.addNewChild(0xff00000012, "role3", 0, 0xff00000043, null as IConcept?)
                t.addNewChild(0x1, "role5", 0, 0xff00000044, null as IConcept?)
                t.moveChild(0xff00000012, "role5", 0, 0xff00000044)
                t.deleteNode(0xff00000043)
                t.deleteNode(0xff00000044)
            },
        )
    }

    @Test
    fun knownIssue14() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role2", 0, 0xff00000011, null as IConcept?)
            },
            { t -> // 0
                t.deleteNode(0xff00000011)
            },
            { t -> // 1
                t.moveChild(0x1, "role1", 0, 0xff00000011)
                t.addNewChild(0x1, "role1", 1, 0xff0000002d, null as IConcept?)
            },
        )
    }

    @Test
    fun knownIssue15() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role5", 0, 0xff00000012, null as IConcept?)
            },
            { t -> // 0
                t.deleteNode(0xff00000012)
            },
            { t -> // 1
                t.addNewChild(0xff00000012, "role1", 0, 0xff00000043, null as IConcept?)
                t.addNewChild(0xff00000012, "role3", 0, 0xff00000045, null as IConcept?)
                t.deleteNode(0xff00000045)
                t.deleteNode(0xff00000043)
            },
        )
    }

    @Test
    fun knownIssue16() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role3", 0, 0xff0000000e, null as IConcept?)
                t.addNewChild(0xff0000000e, "role3", 0, 0xff00000010, null as IConcept?)
                t.addNewChild(0xff00000010, "role3", 0, 0xff00000011, null as IConcept?)
                t.addNewChild(0xff00000010, "role3", 0, 0xff00000012, null as IConcept?)
                t.moveChild(0x1, "role2", 0, 0xff00000011)
            },
            { t -> // 0
                t.deleteNode(0xff00000012)
                t.addNewChild(0x1, "role2", 1, 0xff0000001c, null as IConcept?)
                t.addNewChild(0xff0000001c, "role2", 0, 0xff00000022, null as IConcept?)
                t.deleteNode(0xff00000010)
                t.deleteNode(0xff0000000e)
                t.addNewChild(0xff00000022, "role3", 0, 0xff00000023, null as IConcept?)
            },
            { t -> // 1
                t.addNewChild(0xff00000012, "role3", 0, 0xff00000043, null as IConcept?)
                t.addNewChild(0xff00000043, "role3", 0, 0xff00000044, null as IConcept?)
                t.moveChild(0xff0000000e, "role3", 0, 0xff00000044)
                t.deleteNode(0xff00000043)
            },
        )
    }

    @Test
    fun knownIssue17() {
        knownIssueTest(
            { t ->
            },
            { t -> // 0
                t.addNewChild(0x1, "role1", 0, 0xff00000002, null as IConcept?)
                t.deleteNode(0xff00000002)
            },
            { t -> // 1
                t.addNewChild(0x1, "role2", 0, 0xff0000000d, null as IConcept?)
                t.moveChild(0x1, "role2", 1, 0xff0000000d)
                t.deleteNode(0xff0000000d)
            },
        )
    }

    @Test
    fun knownIssue18() {
        knownIssueTest(
            { t ->
            },
            { t -> // 0
                t.addNewChild(0x1, "role5", 0, 0xff00000043, null as IConcept?)
            },
            { t -> // 1
                t.addNewChild(0x1, "role3", 0, 0xff00000059, null as IConcept?)
                t.addNewChild(0x1, "role5", 0, 0xff0000005c, null as IConcept?)
                t.moveChild(0x1, "role3", 1, 0xff0000005c)
                t.deleteNode(0xff00000059)
            },
        )
    }

    @Test
    fun knownIssue19() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role5", 0, 0xff00000012, null as IConcept?)
            },
            { t -> // 0
                t.addNewChild(0xff00000012, "role2", 0, 0xff00000016, null as IConcept?)
            },
            { t -> // 1
                t.deleteNode(0xff00000012)
            },
        )
    }

    @Test
    fun knownIssue20() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role5", 0, 0xff00000012, null as IConcept?)
            },
            { t -> // 0
                t.addNewChild(0x1, "role6", 0, 0xff00000013, null as IConcept?)
                t.moveChild(0xff00000012, "role2", 0, 0xff00000013)
            },
            { t -> // 1
                t.deleteNode(0xff00000012)
            },
        )
    }

    @Test
    fun knownIssue21() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role3", 0, 0xff0000000e, null as IConcept?)
                t.moveChild(0x1, "role1", 0, 0xff0000000e)
                t.addNewChild(0xff0000000e, "role2", 0, 0xff0000000f, null as IConcept?)
                t.deleteNode(0xff0000000f)
                t.addNewChild(0xff0000000e, "role3", 0, 0xff00000010, null as IConcept?)
                t.moveChild(0xff0000000e, "role3", 0, 0xff00000010)
                t.addNewChild(0xff00000010, "role3", 0, 0xff00000011, null as IConcept?)
                t.addNewChild(0xff00000010, "role3", 0, 0xff00000012, null as IConcept?)
                t.moveChild(0x1, "role2", 0, 0xff00000011)
                t.moveChild(0xff00000011, "role1", 0, 0xff00000010)
                t.moveChild(0xff00000010, "role1", 0, 0xff00000012)
                t.moveChild(0x1, "role2", 1, 0xff00000011)
                t.addNewChild(0x1, "role2", 0, 0xff00000013, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0xff00000012, "role2", 0, 0xff00000013)
            },
            { t -> // 1
                t.moveChild(0x1, "role1", 1, 0xff00000010)
                t.moveChild(0x1, "role1", 1, 0xff00000012)
                t.deleteNode(0xff0000000e)
            },
        )
    }

    @Test
    fun knownIssue22() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role3", 0, 0xff00000011, null as IConcept?)
            },
            { t -> // 0
                t.deleteNode(0xff00000011)
            },
            { t -> // 1
                t.addNewChild(0xff00000011, "role2", 0, 0xff0000002e, null as IConcept?)
                t.addNewChild(0xff00000011, "role3", 0, 0xff00000030, null as IConcept?)
                t.moveChild(0x1, "role3", 0, 0xff0000002e)
            },
        )
    }

    @Test
    fun knownIssue23() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role3", 0, 0xff00000001, null as IConcept?)
                t.moveChild(0x1, "role2", 0, 0xff00000001)
            },
            { t -> // 0
                t.moveChild(0x1, "role2", 1, 0xff00000001)
            },
            { t -> // 1
                t.deleteNode(0xff00000001)
            },
        )
    }

    @Test
    fun knownIssue24() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "role3", 0, 0xff00000003, null as IConcept?)
                t.addNewChild(0xff00000003, "role1", 0, 0xff00000004, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0xff00000003, "role2", 0, 0xff00000004)
            },
            { t -> // 1
                t.deleteNode(0xff00000004)
                t.deleteNode(0xff00000003)
            },
        )
    }

    @Test
    fun knownIssue25() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "cRole2", 0, 0xff00000001, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0x1, "cRole2", 1, 0xff00000001)
            },
            { t -> // 1
                t.moveChild(0x1, "cRole2", 1, 0xff00000001)
            },
        )
    }

    @Test
    fun knownIssue26() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "cRole2", 0, 0xff00000002, null as IConcept?)
                t.addNewChild(0x1, "cRole2", 1, 0xff00000003, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0xff00000002, "cRole3", 0, 0xff00000003)
            },
            { t -> // 1
                t.moveChild(0xff00000003, "cRole3", 0, 0xff00000002)
            },
        )
    }

    @Test
    fun knownIssue27() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "cRole1", 0, 0xff00000003, null as IConcept?)
                t.addNewChild(0x1, "cRole2", 0, 0xff00000004, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0xff00000004, "cRole3", 0, 0xff00000003)
                t.moveChild(0xff00000004, "cRole2", 0, 0xff00000003)
            },
            { t -> // 1
                t.deleteNode(0xff00000004)
            },
        )
    }

    @Test
    fun knownIssue28() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "cRole2", 0, 0xff00000001, null as IConcept?)
                t.addNewChild(0x1, "cRole1", 0, 0xff00000002, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0xff00000002, "cRole2", 0, 0xff00000001)
                t.deleteNode(0xff00000001)
            },
            { t -> // 1
                t.moveChild(0xff00000001, "cRole1", 0, 0xff00000002)
                t.addNewChild(0x1, "cRole1", 0, 0xff00000006, null as IConcept?)
                t.moveChild(0xff00000006, "cRole3", 0, 0xff00000002)
            },
        )
    }

    @Test
    fun knownIssue29() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "cRole2", 0, 0xff00000002, null as IConcept?)
                t.addNewChild(0xff00000002, "cRole2", 0, 0xff00000001, null as IConcept?)
                t.addNewChild(0x1, "cRole3", 0, 0xff00000004, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0xff00000001, "cRole3", 0, 0xff00000004)
            },
            { t -> // 1
                t.moveChild(0xff00000004, "cRole1", 0, 0xff00000002)
            },
        )
    }

    @Test
    fun knownIssue30() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "cRole3", 0, 0xff00000003, null as IConcept?)
                t.addNewChild(0x1, "cRole2", 0, 0xff00000004, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0xff00000004, "cRole1", 0, 0xff00000003)
            },
            { t -> // 1
                t.deleteNode(0xff00000004)
                t.moveChild(0x1, "cRole3", 1, 0xff00000003)
            },
        )
    }

    @Test
    fun knownIssue31() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "cRole2", 0, 0xff00000001, null as IConcept?)
                t.addNewChild(0xff00000001, "cRole1", 0, 0xff00000002, null as IConcept?)
                t.addNewChild(0xff00000002, "cRole2", 0, 0xff00000003, null as IConcept?)
                t.addNewChild(0xff00000001, "cRole1", 0, 0xff00000004, null as IConcept?)
                t.addNewChild(0xff00000003, "cRole2", 0, 0xff00000005, null as IConcept?)
            },
            { t -> // 0
                t.addNewChild(0xff00000002, "cRole1", 0, 0xff00000007, null as IConcept?)
            },
            { t -> // 1
                t.deleteNode(0xff00000005)
                t.moveChild(0xff00000004, "cRole1", 0, 0xff00000002)
                t.moveChild(0x1, "cRole1", 0, 0xff00000004)
            },
        )
    }

    @Test
    fun knownIssue32() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "cRole2", 0, 0xff00000001, null as IConcept?)
                t.addNewChild(0xff00000001, "cRole3", 0, 0xff00000002, null as IConcept?)
                t.addNewChild(0x1, "cRole2", 1, 0xff00000003, null as IConcept?)
                t.addNewChild(0xff00000003, "cRole3", 0, 0xff00000004, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0xff00000004, "cRole3", 0, 0xff00000001)
            },
            { t -> // 1
                t.moveChild(0x1, "cRole3", 0, 0xff00000004)
                t.moveChild(0xff00000001, "cRole3", 1, 0xff00000002)
            },
        )
    }

    @Test
    fun knownIssue33() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "cRole3", 0, 0xff00000001, null as IConcept?)
                t.addNewChild(0xff00000001, "cRole3", 0, 0xff00000002, null as IConcept?)
                t.addNewChild(0xff00000001, "cRole3", 1, 0xff00000003, null as IConcept?)
            },
            { t -> // 0
                t.deleteNode(0xff00000002)
            },
            { t -> // 1
                t.moveChild(0xff00000002, "cRole3", 0, 0xff00000003)
            },
        )
    }

    @Test
    fun knownIssue34() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "cRole3", 0, 0xff00000001, null as IConcept?)
                t.addNewChild(0xff00000001, "cRole2", 0, 0xff00000002, null as IConcept?)
                t.addNewChild(0xff00000001, "cRole2", 1, 0xff00000003, null as IConcept?)
                t.addNewChild(0xff00000002, "cRole2", 0, 0xff00000004, null as IConcept?)
                t.addNewChild(0xff00000001, "cRole1", 0, 0xff00000005, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0x1, "cRole2", 0, 0xff00000005)
                t.moveChild(0x1, "cRole2", 0, 0xff00000003)
            },
            { t -> // 1
                t.moveChild(0xff00000004, "cRole3", 0, 0xff00000003)
                t.deleteNode(0xff00000005)
            },
        )
    }

    @Test
    fun knownIssue35() {
        knownIssueTest(
            { t ->
                t.addNewChild(0x1, "cRole2", 0, 0xff00000001, null as IConcept?)
                t.addNewChild(0x1, "cRole3", 0, 0xff00000002, null as IConcept?)
                t.addNewChild(0xff00000002, "cRole3", 0, 0xff00000003, null as IConcept?)
                t.addNewChild(0xff00000001, "cRole1", 0, 0xff00000004, null as IConcept?)
                t.addNewChild(0xff00000004, "cRole3", 0, 0xff00000005, null as IConcept?)
            },
            { t -> // 0
                t.moveChild(0xff00000003, "cRole3", 0, 0xff00000004)
                t.moveChild(0xff00000003, "cRole3", 0, 0xff00000005)
            },
            { t -> // 1
                t.deleteNode(0xff00000003)
            },
        )
    }

    fun createVersion(opsAndTree: Pair<List<IAppliedOperation>, ITree>, previousVersion: CLVersion?): CLVersion {
        val clTree = opsAndTree.second as CLTree
        return CLVersion.createRegularVersion(
            id = idGenerator.generate(),
            time = null,
            author = null,
            tree = clTree,
            baseVersion = previousVersion,
            operations = opsAndTree.first.map { it.getOriginalOp() }.toTypedArray(),
        )
    }
}

fun assertSameTree(tree1: ITree, tree2: ITree) {
    tree2.visitChanges(
        tree1,
        object : ITreeChangeVisitorEx {
            override fun containmentChanged(nodeId: Long) {
                fail("containmentChanged ${nodeId.toString(16)}")
            }

            override fun childrenChanged(nodeId: Long, role: String?) {
                fail("childrenChanged ${nodeId.toString(16)}, $role")
            }

            override fun referenceChanged(nodeId: Long, role: String) {
                fail("referenceChanged ${nodeId.toString(16)}, $role")
            }

            override fun propertyChanged(nodeId: Long, role: String) {
                fail("propertyChanged ${nodeId.toString(16)}, $role")
            }

            override fun nodeRemoved(nodeId: Long) {
                fail("nodeRemoved ${nodeId.toString(16)}")
            }

            override fun nodeAdded(nodeId: Long) {
                fail("nodeAdded nodeId")
            }
        },
    )
}
