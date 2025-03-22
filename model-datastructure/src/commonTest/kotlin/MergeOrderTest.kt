import org.modelix.model.VersionMerger
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.lazy.runWrite
import org.modelix.model.persistent.MapBaseStore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * This tests indirectly the algorithm in LinearHistory, by creating and merging versions.
 */
class MergeOrderTest {
    private var store: MapBaseStore = MapBaseStore()
    private var storeCache: IAsyncObjectStore = createObjectStoreCache(store)
    private var idGenerator: IIdGenerator = IdGenerator.newInstance(3)

    fun CLVersion.runWrite(id: Long, body: IWriteTransaction.() -> Unit): CLVersion {
        return nextId(id) { runWrite(idGenerator, null, { it.apply(body) }) }
    }
    fun CLVersion.runWrite(body: IWriteTransaction.() -> Unit) = runWrite(idGenerator, null, { it.apply(body) })
    fun merge(v1: CLVersion, v2: CLVersion) = VersionMerger(storeCache, idGenerator).mergeChange(v1, v2)
    fun merge(id: Long, v1: CLVersion, v2: CLVersion) = nextId(id) { VersionMerger(storeCache, idGenerator).mergeChange(v1, v2) }

    @Test
    fun mergeOrderShouldNotMatter() = mergeOrderShouldNotMatter(false)

    @Test
    fun mergeOrderShouldNotMatterAlternativeIds() = mergeOrderShouldNotMatter(true)

    fun mergeOrderShouldNotMatter(alternativeIds: Boolean) {
        val merger = VersionMerger(storeCache, idGenerator)

        val v0 = CLVersion.createRegularVersion(
            idGenerator.generate(),
            null,
            null,
            CLTree(storeCache),
            null,
            emptyArray(),
        )

        // There are two clients working on the same version and both change the same property.
        // This creates a conflict.
        val va1 = v0.runWrite(0xa1) { setProperty(ITree.ROOT_ID, "name", "MyClassA") }
        assertEquals("MyClassA", va1.getTree().getProperty(ITree.ROOT_ID, "name"))
        val vb1 = v0.runWrite(0xb1) { setProperty(ITree.ROOT_ID, "name", "MyClassB") }
        assertEquals("MyClassB", vb1.getTree().getProperty(ITree.ROOT_ID, "name"))

        // Now both clients exchange their modifications ...

        // In the meantime, Client A keeps working on his branch and creates a new version that doesn't actually
        // change anything, so should not have an effect on the following merge.
        // It changes an unrelated part of the model that wouldn't cause a conflict even if the operations were ordered
        // in a completely unexpected way.
        val va2 = va1.runWrite(0xa2L + (if (alternativeIds) 0x100 else 0)) {
            setProperty(ITree.ROOT_ID, "unrelated", "Class renamed")
            setProperty(ITree.ROOT_ID, "unrelated", null)
        }
        assertEquals("MyClassA", va2.getTree().getProperty(ITree.ROOT_ID, "name"))

        // Client B receives the version with the first property change from client A, but not the second empty change.
        val vb2 = merge(0xb2, vb1, va1)
        assertContains(setOf("MyClassA", "MyClassB"), vb2.getTree().getProperty(ITree.ROOT_ID, "name"))

        // Client A receives the version with the property change from client B.
        val va3 = merge(0xa3, va2, vb1)
        assertContains(setOf("MyClassA", "MyClassB"), va3.getTree().getProperty(ITree.ROOT_ID, "name"))

        // The history should now look like this:
        //
        //        v0
        //      /    \
        //    va1     vb1
        //   |    \ /  |
        //  va2   /\   |
        //   |  /   \  |
        //   |/      \ |
        //  va3      vb2
        //
        // - va1 and vb2 contain the property change
        // - va2 didn't change anything
        // - va3 and vb2 are the current head versions that merged both property changes,
        //   and are expected to contain the same resulting model.

        // After Client B receives another update from Client A that includes the empty change va2
        // there shouldn't be any doubt that the merge result has to be identical ...
        assertEquals(
            va3.getTree().getProperty(ITree.ROOT_ID, "name"),
            merger.mergeChange(vb2, va2).getTree().getProperty(ITree.ROOT_ID, "name"),
        )
        assertSameTree(va3.getTree(), merger.mergeChange(vb2, va2).getTree())

        // ... but even the previous merge should have an identical result.
        // It would be confusing for the user if the merge algorithm keeps changing its mind about the result for
        // no obvious reason.
        assertEquals(
            va3.getTree().getProperty(ITree.ROOT_ID, "name"),
            vb2.getTree().getProperty(ITree.ROOT_ID, "name"),
        )
        assertSameTree(va3.getTree(), vb2.getTree())
    }

    fun <R> nextId(id: Long, body: () -> R): R {
        val saved = idGenerator
        try {
            idGenerator = object : IIdGenerator {
                override fun generate(): Long = id
            }
            return body()
        } finally {
            idGenerator = saved
        }
    }
}
