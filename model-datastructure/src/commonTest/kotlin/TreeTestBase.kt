import org.modelix.model.api.IBranch
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.async.IAsyncObjectStore
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.persistent.MapBasedStore
import kotlin.random.Random
import kotlin.test.BeforeTest

open class TreeTestBase {
    protected val DEBUG = false
    protected val roles: List<String> = listOf("role1", "role2", "role3")
    protected var rand: Random = Random(83569)
    protected var store: MapBasedStore = MapBasedStore()
    protected var storeCache: IAsyncObjectStore = createObjectStoreCache(store)
    protected var idGenerator: IdGenerator = IdGenerator.newInstance(3)
    protected var initialTree: ITree = CLTree(storeCache)

    @BeforeTest
    fun setUp() {
        rand = Random(83569)
        store = MapBasedStore()
        storeCache = createObjectStoreCache(store)
        idGenerator = IdGenerator.newInstance(255)
        initialTree = CLTree(storeCache)
    }

    fun applyRandomChange(tree: ITree, expectedTree: ExpectedTreeData): ITree {
        val branch = PBranch(tree, idGenerator)
        applyRandomChange(branch, expectedTree)
        return branch.computeRead { branch.transaction.tree }
    }

    fun applyRandomChange(branch: IBranch, expectedTree: ExpectedTreeData?) {
        RandomTreeChangeGenerator(idGenerator, rand).applyRandomChange(branch, expectedTree)
    }

    fun assertBranch(branch: IBranch, expectedTree: ExpectedTreeData) {
        assertTree(branch.computeRead { branch.transaction.tree }, expectedTree)
    }

    fun assertTree(tree: ITree, expectedTree: ExpectedTreeData) {
        expectedTree.assertTree(tree)
    }
}
