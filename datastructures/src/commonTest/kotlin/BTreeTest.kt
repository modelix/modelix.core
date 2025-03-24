import org.modelix.datastructures.btree.BTree
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class BTreeTest {

    @Test
    fun `can insert node`() = runTest(1_000, 10_000, 5)

    fun runTest(numOperations: Int, keyRange: Int, valueRange: Int) {
        val rand = Random(6734687)
        var tree = BTree<String, String>(2)

        val expected = HashMap<String, String>()

        repeat(numOperations) {
            when (rand.nextInt(5)) {
                0 -> {
                    if (expected.isNotEmpty()) {
                        val key = expected.keys.random(rand)
                        // println("remove $key")
                        tree = tree.remove(key)
                        expected.remove(key)
                    }
                }
                else -> {
                    val key = "k" + rand.nextInt(keyRange).toString()
                    val value = "v" + rand.nextInt(valueRange).toString()
                    // println("insert $key -> $value")
                    tree = tree.put(key, value)
                    expected[key] = value
                }
            }
            tree.validate()

            for (entry in expected.entries) {
                assertEquals(entry.value, tree.get(entry.key), "for key ${entry.key}")
            }
        }
    }
}
