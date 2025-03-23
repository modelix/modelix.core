import org.modelix.datastructures.btree.BTree
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class BTreeTest {

    @Test
    fun `can insert node`() {
        val rand = Random(6734687)
        var tree = BTree<String, String>(minDegree = 2)

        val expected = HashMap<String, String>()

        repeat(1000) {
            when (rand.nextInt(5)) {
                0 -> {
                    if (expected.isNotEmpty()) {
                        val key = expected.keys.random(rand)
                        println("remove $key")
                        tree = tree.remove(key)
                        expected.remove(key)
                    }
                }
                else -> {
                    val key = "k" + rand.nextInt(100).toString()
                    val value = "v" + rand.nextInt(5).toString()
                    println("insert $key -> $value")
                    tree = tree.insert(key, value)
                    expected[key] = value
                }
            }

            for (entry in expected.entries) {
                assertEquals(entry.value, tree.search(entry.key), "for key ${entry.key}")
            }
        }
    }
}