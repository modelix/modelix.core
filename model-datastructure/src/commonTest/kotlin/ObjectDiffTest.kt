import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.IIdGenerator
import org.modelix.model.api.ITree
import org.modelix.model.api.TreePointer
import org.modelix.model.api.async.getDescendantsAndSelf
import org.modelix.model.api.getRootNode
import org.modelix.model.data.asData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.persistent.getTreeObject
import org.modelix.streams.getBlocking
import org.modelix.streams.plus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(DelicateModelixApi::class) // in tests everything is allowed
class ObjectDiffTest {

    @Test
    fun treeDiff() {
        val store = createObjectStoreCache(MapBasedStore())
        val tree1 = CLTree.builder(store).repositoryId("test").build()
            .addNewChild(ITree.ROOT_ID, "childrenA", 0, 100, null as IConceptReference?)
            .addNewChild(ITree.ROOT_ID, "childrenA", 1, 101, null as IConceptReference?)
            .addNewChild(ITree.ROOT_ID, "childrenA", 1, 102, null as IConceptReference?)
        val tree2 = tree1
            .setProperty(100, "name", "a")
            .setProperty(102, "name", "b")

        val diff = tree2.getTreeObject().objectDiff(tree1.getTreeObject())
        val diffString = diff.map { it.getHashString() + " -> " + it.data.serialize() }.asSequence().joinToString("\n")

        val allObjects = tree1.getTreeObject().getDescendantsAndSelf().plus(diff).asSequence()
        val store2 = createObjectStoreCache(MapBasedStore().also { it.putAll(allObjects.associate { it.getHashString() to it.data.serialize() }) })
        val tree3 = CLTree.fromHash(tree2.getTreeObject().getHashString(), store2)

        tree3.asAsyncTree().getDescendantsAndSelf(ITree.ROOT_ID).asSequence()

        val expected = """
            MJFEu*_hdxrmyX2F8pWUh8pHO5Meoivd5BsZCOd04OmE -> test/3/BRS9r*d8eT0RP6Uadz--7yzhhyOO14NiQB0uykMibl_E
            BRS9r*d8eT0RP6Uadz--7yzhhyOO14NiQB0uykMibl_E -> S/11/0/aWYDj*Aovk93dr7BCZLE26G6SVscuCObD2Zih1j5_35I
            aWYDj*Aovk93dr7BCZLE26G6SVscuCObD2Zih1j5_35I -> I/41/IED_Q*pDKY2iEbzd_FIQJW1E8L4rMTGjW3Nt2fztvnbU,3wS1-*vioMR3Oecn25uZWTCfvi6_hjjHqVg0LGZGKJ1E
            3wS1-*vioMR3Oecn25uZWTCfvi6_hjjHqVg0LGZGKJ1E -> I/1500/bQ1qn*eX7Tet6eTq54-59MKY2-B45VlApKU9MG5cCTSs,0t8kq*t8nrdkZ0vh9JUgcbQQzwGWLwAwY-K8ZqyOs6Dc,wQKjH*0HeK2QG_Xa3sOsqNKddgYBRomqqr18JRSP7N1U
            bQ1qn*eX7Tet6eTq54-59MKY2-B45VlApKU9MG5cCTSs -> L/64/cTkqm*040KM6jQi74_6ObkWuuGAfR3RUgfGVxyYExki0
            cTkqm*040KM6jQi74_6ObkWuuGAfR3RUgfGVxyYExki0 -> 64/%00/1/childrenA//name=a/
            wQKjH*0HeK2QG_Xa3sOsqNKddgYBRomqqr18JRSP7N1U -> L/66/sY5P2*keSXjtlgyOYlM0PJVLUmKDbQx4SIwa5tkxy3HQ
            sY5P2*keSXjtlgyOYlM0PJVLUmKDbQx4SIwa5tkxy3HQ -> 66/%00/1/childrenA//name=b/
        """.trimIndent()

        assertEquals(expected, diffString)
    }

    @Test
    fun randomChange() {
        for (i in 1..10) {
            println(i)
            runRandomChange(Random(i + 67872346))
        }
    }

    fun runRandomChange(rand: Random) {
        val store1 = createObjectStoreCache(MapBasedStore())
        val idGenerator = object : IIdGenerator {
            private val usedIds = HashSet<Long>().also { it.add(ITree.ROOT_ID) }
            override fun generate(): Long {
                var candidate: Long
                do {
                    candidate = rand.nextLong(2L, (usedIds.size * 5L).coerceAtLeast(10L))
                } while (usedIds.contains(candidate))
                usedIds.add(candidate)
                return candidate
            }
        }
        val changeGenerator1 = RandomTreeChangeGenerator(idGenerator, rand).growingOperationsOnly()
        val changeGenerator2 = RandomTreeChangeGenerator(idGenerator, rand)
        var initialTree: ITree = CLTree.builder(store1).repositoryId("test").build()
        repeat(100) {
            initialTree = changeGenerator1.applyRandomChange(initialTree, null)
        }

        var newTree = initialTree
        repeat(100) {
            newTree = changeGenerator2.applyRandomChange(newTree, null)
        }

        val diff = newTree.getTreeObject().objectDiff(initialTree.getTreeObject()).toList().getBlocking(store1)
        val initialObjects = initialTree.getTreeObject().getDescendantsAndSelf().toList().getBlocking(store1)
        val newObjects = newTree.getTreeObject().getDescendantsAndSelf().toList().getBlocking(store1)
        val unnecessaryObjects = (diff.associateBy { it.getHashString() } - newObjects.map { it.getHashString() }.toSet()).values.toSet()

        assertEquals(emptySet(), unnecessaryObjects)

        val allObjects = initialObjects + diff

        val store2 = createObjectStoreCache(
            MapBasedStore().also {
                it.putAll(allObjects.associate { it.getHashString() to it.data.serialize() })
            },
        )

        val restoredTree = CLTree.fromHash(newTree.getTreeObject().getHashString(), store2)

        val expectedNodes = TreePointer(newTree).getRootNode().asData()
        val restoredNodes = TreePointer(restoredTree).getRootNode().asData()

        assertEquals(expectedNodes, restoredNodes)
    }
}
