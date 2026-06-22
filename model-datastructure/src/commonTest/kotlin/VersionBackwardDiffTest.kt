import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.lazy.diff
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.persistent.getTreeObject
import org.modelix.streams.getBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #1042: when a peer that already holds a version `B` requests an *older* version `T` (an ancestor of `B`,
 * i.e. backward navigation between readonly versions), the delta must contain the tree objects that are
 * unique to `T` so the peer can fully load `T`. Previously `IVersion.diff` short-circuited to an empty
 * stream whenever `T` added no new history over `B`, which is always true going backward, leaving the
 * client without `T`'s tree -> "Entry not found".
 */
class VersionBackwardDiffTest {

    private val store = createObjectStoreCache(MapBasedStore())

    private fun treeHashes(tree: ITree): Set<String> =
        tree.getTreeObject().getDescendantsAndSelf().toList().getBlocking(store).map { it.getHashString() }.toSet()

    @Test
    fun backwardDeltaContainsTargetTreeObjects() {
        val tree1 = CLTree.builder(store).repositoryId("test").build()
            .addNewChild(ITree.ROOT_ID, "children", 0, 100, null as IConceptReference?)
            .addNewChild(ITree.ROOT_ID, "children", 1, 101, null as IConceptReference?)
        val v1 = CLVersion.createRegularVersion(
            id = 1L,
            time = null,
            author = null,
            tree = tree1,
            baseVersion = null,
            operations = emptyArray(),
        )

        val tree2 = tree1
            .setProperty(100, "name", "changed")
            .addNewChild(ITree.ROOT_ID, "children", 2, 102, null as IConceptReference?)
        val v2 = CLVersion.createRegularVersion(
            id = 2L,
            time = null,
            author = null,
            tree = tree2,
            baseVersion = v1,
            operations = emptyArray(),
        )

        val v1TreeHashes = treeHashes(tree1)
        val v2TreeHashes = treeHashes(tree2)

        // Both the default filter and the lean (readonly) filter must yield a complete backward delta.
        val filters = listOf(
            ObjectDeltaFilter(),
            ObjectDeltaFilter(includeHistory = false, includeOperations = false, includeTrees = true),
        )
        for (filter in filters) {
            // A peer that holds v2's tree asks for the older v1 as a delta against v2.
            val deltaHashes = v1.diff(listOf(v2), filter).toList().getBlocking(store)
                .map { it.getHashString() }.toSet()

            // Everything needed to load v1's tree must be available from v2's tree plus the delta.
            val missing = v1TreeHashes - (v2TreeHashes + deltaHashes)
            assertEquals(emptySet(), missing, "filter=$filter omitted v1 tree objects: $missing")
        }
    }
}
