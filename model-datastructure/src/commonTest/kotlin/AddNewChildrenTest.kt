import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.async.getDescendants
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.persistent.MapBasedStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class AddNewChildrenTest {

    @Test
    fun addManyChildren() {
        // Previously, a large number of nodes resulted in a stack overflow, because they were added to the nodes map
        // one by one using a fold operation that created a long observable chain.
        // Now, there is a new putAll operation that updates the subtrees of the persistent map in parallel.

        val store = createObjectStoreCache(MapBasedStore())
        val tree = CLTree(store)
        val childIds = (1_000L..11_000L).toList()
        val newTree = tree.addNewChildren(
            ITree.ROOT_ID,
            null,
            0,
            childIds.toLongArray(),
            childIds.map { null as IConceptReference? }.toTypedArray(),
        )
        assertEquals(childIds, newTree.getChildren(ITree.ROOT_ID, null))
    }

    @Test
    fun addManyDescendants() {
        val store = createObjectStoreCache(MapBasedStore())
        var tree: ITree = CLTree(store)
        val rand = Random(56643)
        val roles = listOf("a", "b", "c")
        val idGenerator = IdGenerator.newInstance(0xff)

        val expectedNodes = HashSet<Long>().also { it.add(ITree.ROOT_ID) }
        val trees = ArrayList<ITree>()
        trees.add(tree)
        repeat(100) {
            val parent = tree.getAllNodes().random(rand)
            val role = roles.random(rand)
            val childIds = idGenerator.generate(3).toList().toLongArray()
            expectedNodes.addAll(childIds.asIterable())

            tree = tree.addNewChildren(
                parent,
                role,
                -1,
                childIds,
                childIds.map { null as IConceptReference? }.toTypedArray(),
            )

            trees.add(tree)
            assertEquals(expectedNodes, tree.getAllNodes().toSet())
        }
    }

    private fun ITree.getAllNodes(): List<Long> {
        val asyncTree = asAsyncTree()
        return asyncTree.getStreamExecutor().query { asyncTree.getDescendants(ITree.ROOT_ID, true).toList() }
    }
}
