import org.modelix.model.api.ITree
import kotlin.test.Test

class TreeTest : TreeTestBase() {
    @Test
    fun test_random() {
        var tree: ITree = initialTree
        val expectedTree = ExpectedTreeData()

        for (i in 0..999) {
            if (i % 100 == 0) storeCache.clearCache()
            tree = applyRandomChange(tree, expectedTree)
            assertTree(tree, expectedTree)
        }
    }
}
