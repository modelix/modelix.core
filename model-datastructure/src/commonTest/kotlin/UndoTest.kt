import org.modelix.model.LinearHistory
import org.modelix.model.VersionMerger
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.operations.IAppliedOperation
import org.modelix.model.operations.OTBranch
import org.modelix.model.operations.UndoOp
import org.modelix.model.persistent.MapBaseStore
import kotlin.random.Random
import kotlin.test.Test

class UndoTest {

    @Test
    fun undo_random() {
        /*
            digraph G {
              1 [label="id=1"]
              2 [label="id=2\nv[0]"]
              3 [label="id=3\nv[1]"]
              4 [label="id=4\nv[2]"]
              undo [label="id=5\nversion_1_1u\n>>undo v[1]<<"]
              version_0_1 [label="id=30064771077\nversion_0_1"]
              version_2_1 [label="id=30064771078\nversion_2_1"]

              2 -> 1
              3 -> 1
              4 -> 1
              undo -> 3
              version_0_1 -> 2
              version_0_1 -> 3
              version_2_1 -> 3
              version_2_1 -> 4
              version_0_1_1u -> version_0_1
              version_0_1_1u -> undo
              version_2_1_1u -> undo
              version_2_1_1u -> version_2_1
            }
         */
        val idGenerator = IdGenerator.newInstance(7)
        val versionIdGenerator = IdGenerator.newInstance(0)
        val store = createObjectStoreCache(MapBaseStore())
        val merger = VersionMerger(store, idGenerator)
        val baseBranch = OTBranch(PBranch(CLTree(store), idGenerator), idGenerator)
        val rand = Random(347663)

        randomChanges(baseBranch, 50, idGenerator, rand)
        val baseVersion = createVersion(baseBranch.operationsAndTree, null, versionIdGenerator)

        val maxIndex = 2
        val branches = (0..maxIndex).map { OTBranch(PBranch(baseVersion.tree, idGenerator), idGenerator) }.toList()
        for (i in 0..maxIndex) {
            branches[i].runWrite {
                randomChanges(branches[i], 50, idGenerator, rand)
            }
        }
        val versions = branches.map { branch ->
            createVersion(branch.operationsAndTree, baseVersion, versionIdGenerator)
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
        val store = createObjectStoreCache(MapBaseStore())
        val merger = VersionMerger(store, idGenerator)
        val baseBranch = OTBranch(PBranch(CLTree(store), idGenerator), idGenerator)
        val rand = Random(347663)

        randomChanges(baseBranch, 50, idGenerator, rand)
        val baseVersion = createVersion(baseBranch.operationsAndTree, null, versionIdGenerator)

        val maxIndex = 2
        val branches = (0..maxIndex).map { OTBranch(PBranch(baseVersion.tree, idGenerator), idGenerator) }.toList()
        for (i in 0..maxIndex) {
            branches[i].runWrite {
                randomChanges(branches[i], 50, idGenerator, rand)
            }
        }
        val versions = branches.map { branch ->
            createVersion(branch.operationsAndTree, baseVersion, versionIdGenerator)
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

    fun printHistory(version: CLVersion, store: IAsyncObjectStore) {
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
            operations = arrayOf(UndoOp(version.resolvedData.ref)),
        )
    }

    private fun randomChanges(baseBranch: OTBranch, numChanges: Int, idGenerator: IIdGenerator, rand: Random) {
        baseBranch.runWrite {
            val changeGenerator = RandomTreeChangeGenerator(idGenerator, rand).growingOperationsOnly()
            for (i in 0 until (numChanges / 2)) {
                changeGenerator.applyRandomChange(baseBranch, null)
            }
            val changeGenerator2 = RandomTreeChangeGenerator(idGenerator, rand)
            for (i in 0 until (numChanges / 2)) {
                changeGenerator2.applyRandomChange(baseBranch, null)
            }
        }
    }

    fun createVersion(
        opsAndTree: Pair<List<IAppliedOperation>, ITree>,
        previousVersion: CLVersion?,
        idGenerator: IIdGenerator,
    ): CLVersion {
        return CLVersion.createRegularVersion(
            id = idGenerator.generate(),
            time = null,
            author = null,
            tree = opsAndTree.second,
            baseVersion = previousVersion,
            operations = opsAndTree.first.map { it.getOriginalOp() }.toTypedArray(),
        )
    }
}
