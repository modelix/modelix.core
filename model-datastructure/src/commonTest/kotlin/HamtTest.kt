import org.modelix.datastructures.hamt.HamtInternalNode
import org.modelix.datastructures.hamt.HamtNode
import org.modelix.datastructures.hamt.HamtTree
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.LongDataTypeConfiguration
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.ObjectReferenceDataTypeConfiguration
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.MapBaseStore
import org.modelix.streams.IStream
import org.modelix.streams.getBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(DelicateModelixApi::class) // in tests everything is allowed
class HamtTest {
    @Test
    fun test_random() {
        val rand = Random(1)
        val expectedMap: MutableMap<Long, Long> = HashMap()
        val store = MapBaseStore()
        val storeCache = createObjectStoreCache(store)
        val graph = storeCache.asObjectGraph()
        val config = HamtNode.Config(
            graph = graph,
            keyConfig = LongDataTypeConfiguration(),
            valueConfig = ObjectReferenceDataTypeConfiguration(graph, CPNode),
        )
        var hamt = HamtTree(HamtInternalNode.createEmpty(config))
        for (i in 0..999) {
            if (expectedMap.isEmpty() || rand.nextBoolean()) {
                // add entry
                val key = rand.nextInt(1000).toLong()
                val value = rand.nextLong()
                hamt = hamt.put(key, createEntry(value, graph)).getBlocking(hamt)
                expectedMap[key] = value
            } else {
                val keys: List<Long> = ArrayList(expectedMap.keys)
                val key = keys[rand.nextInt(keys.size)]
                if (rand.nextBoolean()) {
                    // remove entry
                    hamt = hamt.remove(key).getBlocking(hamt)
                    expectedMap.remove(key)
                } else {
                    // replace entry
                    val value = rand.nextLong()
                    hamt = hamt.put(key, createEntry(value, graph)).getBlocking(hamt)
                    expectedMap[key] = value
                }
            }
            storeCache.clearCache()
            for ((key, value) in expectedMap) {
                assertEquals(value, hamt.get(key).flatMapZeroOrOne { it.resolve() }.getBlocking(hamt)!!.data.id)
            }
        }
    }

    private fun createEntry(id: Long, factory: IObjectReferenceFactory) = factory(createNode(id))

    private fun createNode(id: Long) = CPNode.create(
        id,
        null,
        0,
        null,
        longArrayOf(),
        arrayOf(),
        arrayOf(),
        arrayOf(),
        arrayOf(),
    )

    @Test
    fun test_random_case_causing_outofbounds_on_js() {
        val store = MapBaseStore()
        val storeCache = createObjectStoreCache(store)
        val graph = storeCache.asObjectGraph()
        val config = HamtNode.Config(
            graph = graph,
            keyConfig = LongDataTypeConfiguration(),
            valueConfig = ObjectReferenceDataTypeConfiguration(graph, CPNode),
        )
        var hamt = HamtTree(HamtInternalNode.createEmpty(config))
        var getId = { e: IStream.ZeroOrOne<ObjectReference<CPNode>> -> e.flatMapZeroOrOne { it.resolve() }.getBlocking(hamt)!!.data.id }

        hamt = hamt.put(965L, createEntry(-6579471327666419615, graph)).getBlocking(hamt)
        hamt = hamt.put(949L, createEntry(4912341421267007347, graph)).getBlocking(hamt)
        assertEquals(4912341421267007347, getId(hamt.get(949L)))
        hamt = hamt.put(260L, createEntry(4166750678024106842, graph)).getBlocking(hamt)
        assertEquals(4166750678024106842, getId(hamt.get(260L)))
        hamt = hamt.put(794L, createEntry(5492533034562136353, graph)).getBlocking(hamt)
        hamt = hamt.put(104L, createEntry(-6505928823483070382, graph)).getBlocking(hamt)
        hamt = hamt.put(47L, createEntry(3122507882718949737, graph)).getBlocking(hamt)
        hamt = hamt.put(693L, createEntry(-2086105010854963537, graph)).getBlocking(hamt)
        storeCache.clearCache()
        // assertEquals(69239088, (hamt!!.getData() as CPHamtInternal).bitmap)
        // assertEquals(6, (hamt!!.getData() as CPHamtInternal).children.count())
        assertEquals(-2086105010854963537, getId(hamt.get(693L)))
    }

    /**
     * It's important that all clients end up with the same version hash even if they apply the same conflict free
     * operations, but just in a different order. This allows them to sync their replica of the model by just applying
     * the operations of a new version instead of downloading the new snapshot (which would require multiple requests).
     */
    @Test
    fun insertionOrderTest() {
        val store = createObjectStoreCache(MapBaseStore())
        val graph = store.asObjectGraph()
        val config = HamtNode.Config(
            graph = graph,
            keyConfig = LongDataTypeConfiguration(),
            valueConfig = ObjectReferenceDataTypeConfiguration(graph, CPNode),
        )
        var emptyMap = HamtTree(HamtInternalNode.createEmpty(config))

        val rand = Random(123456789L)
        val entries = HashMap<Long, ObjectReference<CPNode>>()
        for (i in 1..10) {
            for (k in 1..500) {
                val id = i * 1_000_000L + k
                entries[id] = createEntry(id, graph)
            }
        }
        val keysToRemove = entries.keys.shuffled(rand).take(1000)

        var expectedHash: String? = null

        for (i in 1..10) {
            var map = emptyMap
            entries.entries.shuffled(rand).forEach { map = map.put(it.key, it.value).getBlocking(map) }
            keysToRemove.forEach { map = map.remove(it).getBlocking(map) }
            val hash = map.asObject().getHashString()
            if (i == 1) {
                expectedHash = hash
            } else {
                assertEquals(expectedHash!!, hash)
            }
        }
    }
}
