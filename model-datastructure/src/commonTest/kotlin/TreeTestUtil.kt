import org.modelix.model.api.ITree
import kotlin.random.Random

class TreeTestUtil(private val tree: ITree, private val rand: Random) {
    fun getAncestors(descendant: Long, includeSelf: Boolean): Iterable<Long> {
        if (descendant == 0L) {
            return emptyList()
        }
        return if (includeSelf) {
            (sequenceOf(descendant) + getAncestors(descendant, false)).asIterable()
        } else {
            val parent = tree.getParent(descendant)
            getAncestors(parent, true)
        }
    }

    val allNodes: Iterable<Long>
        get() = getDescendants(ITree.ROOT_ID, true)

    val allNodesWithoutRoot: Iterable<Long>
        get() = getDescendants(ITree.ROOT_ID, false)

    fun getDescendants(parent: Long, includeSelf: Boolean): Iterable<Long> {
        return if (includeSelf) {
            sequenceOf(parent).plus(getDescendants(parent, false)).asIterable()
        } else {
            tree.getAllChildren(parent).flatMap { it: Long -> getDescendants(it, true) }
        }
    }

    val randomNodeWithoutRoot: Long
        get() = getRandomNode(allNodesWithoutRoot)

    val randomNodeWithRoot: Long
        get() = getRandomNode(allNodes)

    fun getRandomNode(nodes: Iterable<Long>): Long {
        return if (nodes.count() == 0) {
            0L
        } else {
            nodes.drop(rand.nextInt(nodes.count())).first()
        }
    }

    val randomLeafNode: Long
        get() = getRandomNode(allNodes.filter { tree.getAllChildren(it).count() == 0 })
}
