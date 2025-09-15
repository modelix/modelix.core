import kotlinx.datetime.Instant
import org.modelix.datastructures.model.HistoryIndexNode
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.height
import org.modelix.datastructures.model.merge
import org.modelix.datastructures.model.size
import org.modelix.datastructures.objects.FullyLoadedObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.asObject
import org.modelix.model.IVersion
import org.modelix.model.TreeId
import org.modelix.model.lazy.CLVersion
import org.modelix.streams.getBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class HistoryIndexTest {

    private fun createInitialVersion(): CLVersion {
        val emptyTree = IGenericModelTree.builder()
            .withInt64Ids()
            .treeId(TreeId.fromUUID("69dcb381-3dba-4251-b3ae-7aafe587c28e"))
            .graph(FullyLoadedObjectGraph())
            .build()

        return IVersion.builder()
            .tree(emptyTree)
            .time(Instant.fromEpochMilliseconds(1757582404557L))
            .build() as CLVersion
    }

    private fun newVersion(
        base: CLVersion,
        author: String? = "unit-test@modelix.org",
        timeDeltaSeconds: Long = 1L,
    ): CLVersion {
        return IVersion.builder()
            .tree(base.getModelTree())
            .author(author)
            .time(base.getTimestamp()!!.plus(timeDeltaSeconds.seconds))
            .build() as CLVersion
    }

    @Test
    fun `1 version`() {
        val history = HistoryIndexNode.of(createInitialVersion().obj)
        assertEquals(1, history.size)
        assertEquals(1, history.height)
    }

    @Test
    fun `2 versions`() {
        val version1 = createInitialVersion()
        val version2 = newVersion(version1)
        val history = HistoryIndexNode.of(version1.obj, version2.obj)
        assertEquals(2, history.size)
        assertEquals(2, history.height)
    }

    @Test
    fun `3 versions`() {
        val version1 = createInitialVersion()
        val version2 = newVersion(version1)
        val version3 = newVersion(version2)
        val graph = version1.graph
        val history1 = HistoryIndexNode.of(version1.obj, version2.obj).asObject(graph)
        val history2 = HistoryIndexNode.of(version3.obj).asObject(graph)
        val history = history1.merge(history2)
        assertEquals(3, history.size)
        assertEquals(3, history.height)
    }

    @Test
    fun `4 versions`() {
        val version1 = createInitialVersion()
        val version2 = newVersion(version1)
        val version3 = newVersion(version2)
        val version4 = newVersion(version3)
        val graph = version1.graph
        val history = HistoryIndexNode.of(version1.obj, version2.obj).asObject(graph)
            .merge(HistoryIndexNode.of(version3.obj).asObject(graph))
            .merge(HistoryIndexNode.of(version4.obj).asObject(graph))
        assertEquals(4, history.size)
        assertEquals(4, history.height)
    }

    @Test
    fun `5 versions`() {
        val version1 = createInitialVersion()
        val version2 = newVersion(version1)
        val version3 = newVersion(version2)
        val version4 = newVersion(version3)
        val version5 = newVersion(version4)
        val graph = version1.graph
        val history = HistoryIndexNode.of(version1.obj, version2.obj).asObject(graph)
            .merge(HistoryIndexNode.of(version3.obj).asObject(graph))
            .merge(HistoryIndexNode.of(version4.obj).asObject(graph))
            .merge(HistoryIndexNode.of(version5.obj).asObject(graph))
        assertEquals(5, history.size)
        assertEquals(4, history.height)
    }

    @Test
    fun `branch and merge`() {
        val version1 = createInitialVersion()
        val version2 = newVersion(version1, author = "user0")

        val version3a = newVersion(version2, author = "user1")
        val version4a = newVersion(version3a, author = "user1")
        val version5a = newVersion(version4a, author = "user1")

        val version3b = newVersion(version2, author = "user2")
        val version4b = newVersion(version3b, author = "user2")
        val version5b = newVersion(version4b, author = "user2")
        val version6b = newVersion(version5b, author = "user2")

        val version7 = IVersion.builder()
            .tree(version1.getModelTree())
            .time(version6b.getTimestamp()!!.plus(1.seconds))
            .autoMerge(version2, version5a, version6b)
            .build() as CLVersion
        val version8 = newVersion(version7, author = "user3")

        val graph = version1.graph
        val historyA = HistoryIndexNode.of(version1.obj, version2.obj).asObject(graph)
            .merge(HistoryIndexNode.of(version3a.obj).asObject(graph))
            .merge(HistoryIndexNode.of(version4a.obj).asObject(graph))
            .merge(HistoryIndexNode.of(version5a.obj).asObject(graph))
        val historyB = HistoryIndexNode.of(version1.obj, version2.obj).asObject(graph)
            .merge(HistoryIndexNode.of(version3b.obj).asObject(graph))
            .merge(HistoryIndexNode.of(version4b.obj).asObject(graph))
            .merge(HistoryIndexNode.of(version5b.obj).asObject(graph))
            .merge(HistoryIndexNode.of(version6b.obj).asObject(graph))
        val history = historyA
            .merge(historyB)
            .merge(HistoryIndexNode.of(version7.obj).asObject(graph))
            .merge(HistoryIndexNode.of(version8.obj).asObject(graph))

        assertEquals(
            listOf(version1, version2, version3a, version3b, version4a, version4b, version5a, version5b, version6b, version7, version8).map { it.getObjectHash() },
            history.data.getAllVersions().map { it.getHash() }.toList().getBlocking(graph),
        )
        assertEquals(11, history.size)
        assertEquals(5, history.height)
    }

    @Test
    fun `large history linear`() {
        val versions = mutableListOf(createInitialVersion())
        repeat(1000) {
            versions.add(newVersion(versions.last()))
        }
        val graph = versions.first().graph
        val history = versions.drop(1).fold(HistoryIndexNode.of(versions.first().asObject()).asObject(graph)) { acc, it ->
            acc.merge(HistoryIndexNode.of(it.asObject()).asObject(graph))
        }
        assertEquals(versions.size.toLong(), history.size)
        assertEquals(
            versions.map { it.getObjectHash() },
            history.data.getAllVersions().map { it.getHash() }.toList().getBlocking(graph),
        )
        assertEquals(11, history.height)
    }

    @Test
    fun `large history shuffled`() {
        val versions = mutableListOf(createInitialVersion())
        repeat(1000) {
            versions.add(newVersion(versions.last()))
        }
        val graph = versions.first().graph
        val history = versions.drop(1).shuffled(Random(78234554)).fold(HistoryIndexNode.of(versions.first().asObject()).asObject(graph)) { acc, it ->
            acc.merge(HistoryIndexNode.of(it.asObject()).asObject(graph))
        }
        assertEquals(versions.size.toLong(), history.size)
        assertEquals(
            versions.map { it.getObjectHash() },
            history.data.getAllVersions().map { it.getHash() }.toList().getBlocking(graph),
        )
        assertEquals(13, history.height)
    }

    @Test
    fun `large history linear recursive`() {
        val versions = mutableListOf(createInitialVersion())
        repeat(1000) {
            versions.add(newVersion(versions.last()))
        }
        val graph = versions.first().graph

        val history = buildHistory(versions)
        assertEquals(versions.size.toLong(), history.size)
        assertEquals(
            versions.map { it.getObjectHash() },
            history.data.getAllVersions().map { it.getHash() }.toList().getBlocking(graph),
        )
        assertEquals(11, history.height)
    }

    @Test
    fun `large history shuffled recursive`() {
        val versions = mutableListOf(createInitialVersion())
        repeat(1000) {
            versions.add(newVersion(versions.last()))
        }
        val graph = versions.first().graph

        val history = buildHistory(versions.shuffled(Random(78234554)))
        assertEquals(versions.size.toLong(), history.size)
        assertEquals(
            versions.map { it.getObjectHash() },
            history.data.getAllVersions().map { it.getHash() }.toList().getBlocking(graph),
        )
        assertEquals(14, history.height)
    }

    private fun buildHistory(versions: List<CLVersion>): Object<HistoryIndexNode> {
        if (versions.size == 1) return HistoryIndexNode.of(versions.single().obj).asObject(versions.single().graph)
        val centerIndex = versions.size / 2
        return buildHistory(versions.subList(0, centerIndex)).merge(buildHistory(versions.subList(centerIndex, versions.size)))
    }
}
