import org.modelix.datastructures.btree.BTreeNode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class BTreeTest {

    @Test
    fun `can insert node`() {
        val rand = Random(6734687)
        var tree = BTreeNode<String, String>(2)

        val expected = HashMap<String, String>()

        repeat(100000) {
            when (rand.nextInt(5)) {
                0 -> {
                    if (expected.isNotEmpty()) {
                        val key = expected.keys.random(rand)
                        //println("remove $key")
                        tree = tree.remove(key).createRoot()
                        expected.remove(key)
                    }
                }
                else -> {
                    val key = "k" + rand.nextInt(10000).toString()
                    val value = "v" + rand.nextInt(5).toString()
                    //println("insert $key -> $value")
                    tree = tree.put(key, value).createRoot()
                    expected[key] = value
                }
            }

//            for (entry in expected.entries) {
//                if (entry.value != tree.get(entry.key)) {
//                    println("stop")
//                }
//                assertEquals(entry.value, tree.get(entry.key), "for key ${entry.key}")
//            }
        }
    }
}
