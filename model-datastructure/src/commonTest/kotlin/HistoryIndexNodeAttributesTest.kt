import kotlinx.datetime.Instant
import org.modelix.datastructures.history.AttributeValuesAggregation
import org.modelix.datastructures.history.AttributesAggregation
import org.modelix.datastructures.history.HistoryIndexNode
import org.modelix.datastructures.history.merge
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.objects.FullyLoadedObjectGraph
import org.modelix.datastructures.objects.asObject
import org.modelix.model.IVersion
import org.modelix.model.TreeId
import org.modelix.model.lazy.CLVersion
import org.modelix.streams.getBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class HistoryIndexNodeAttributesTest {

    private fun createInitialVersion(attrs: Map<String, String> = emptyMap()): CLVersion {
        val graph = FullyLoadedObjectGraph()
        val tree = IGenericModelTree.builder()
            .withInt64Ids()
            .treeId(TreeId.fromUUID("69dcb381-3dba-4251-b3ae-7aafe587c28f"))
            .graph(graph)
            .build()
        val builder = IVersion.builder()
            .tree(tree)
            .time(Instant.fromEpochMilliseconds(1757582404000L))
        attrs.forEach { (k, v) -> builder.attribute(k, v) }
        return builder.build() as CLVersion
    }

    private fun newVersion(
        base: CLVersion,
        attrs: Map<String, String> = emptyMap(),
        timeDeltaSeconds: Long = 1L,
    ): CLVersion {
        val builder = IVersion.builder()
            .tree(base.getModelTree())
            .time(base.getTimestamp()!!.plus(timeDeltaSeconds.seconds))
        attrs.forEach { (k, v) -> builder.attribute(k, v) }
        return builder.build() as CLVersion
    }

    // --- of() ---

    @Test
    fun `of() populates attributes from version`() {
        val v = createInitialVersion(mapOf("env" to "ci", "run" to "42"))
        val node = HistoryIndexNode.of(v.obj)
        assertEquals(AttributesAggregation.of(mapOf("env" to "ci", "run" to "42")), node.attributes)
    }

    @Test
    fun `of() produces empty attributes when version has none`() {
        val v = createInitialVersion()
        val node = HistoryIndexNode.of(v.obj)
        assertEquals(AttributesAggregation.EMPTY, node.attributes)
    }

    // --- aggregation through merge/concat ---

    @Test
    fun `attributes are unioned when two leaf nodes at different times are merged`() {
        val v1 = createInitialVersion(mapOf("env" to "staging"))
        val v2 = newVersion(v1, mapOf("env" to "prod", "run" to "1"))
        val graph = v1.graph
        val node = HistoryIndexNode.of(v1.obj).asObject(graph)
            .merge(HistoryIndexNode.of(v2.obj).asObject(graph))
            .getBlocking(graph)
        assertEquals(AttributeValuesAggregation.of("staging", "prod"), node.data.attributes.getValues("env"))
        assertEquals(AttributeValuesAggregation.of("1"), node.data.attributes.getValues("run"))
    }

    @Test
    fun `attributes are unioned when two leaf nodes at the same time are merged`() {
        val v1 = createInitialVersion(mapOf("env" to "a"))
        val v2 = createInitialVersion(mapOf("env" to "b", "run" to "1"))
        // Same time: both leaves collapse into one leaf
        val graph = v1.graph
        val node = HistoryIndexNode.of(v1.obj).asObject(graph)
            .merge(HistoryIndexNode.of(v2.obj).asObject(graph))
            .getBlocking(graph)
        assertEquals(AttributeValuesAggregation.of("a", "b"), node.data.attributes["env"])
        assertEquals(AttributeValuesAggregation.of("1"), node.data.attributes["run"])
    }

    // --- serialization round-trip ---

    @Test
    fun `leaf node with attributes survives serialize-deserialize round trip`() {
        val v = createInitialVersion(mapOf("env" to "ci", "run" to "99"))
        val graph = v.graph
        val original = HistoryIndexNode.of(v.obj).asObject(graph)
        val serialized = original.data.serialize()
        val restored = HistoryIndexNode.deserialize(serialized, graph)
        assertEquals(original.data.attributes, restored.attributes)
    }

    @Test
    fun `range node attributes field survives serialize-deserialize round trip`() {
        val v1 = createInitialVersion(mapOf("env" to "ci"))
        val v2 = newVersion(v1, mapOf("env" to "prod"))
        val graph = v1.graph
        val original = HistoryIndexNode.of(v1.obj).asObject(graph)
            .merge(HistoryIndexNode.of(v2.obj).asObject(graph))
            .getBlocking(graph)
        val serialized = original.data.serialize()
        // Range node serializes attributes in field index 8 (0-based, LEVEL1-delimited).
        // Extract and deserialize just that field rather than the full node, because the full
        // deserialization requires the child HistoryIndexNode objects to be resolvable in the graph.
        val parts = serialized.split("/")
        val attrPart = parts.getOrNull(8) ?: ""
        val restoredAttrs = HistoryIndexNode.deserializeAttributes(attrPart)
        assertEquals(original.data.attributes, restoredAttrs)
    }

    @Test
    fun `leaf node without attributes field (legacy format) deserializes as empty`() {
        val v = createInitialVersion()
        val graph = v.graph
        val serialized = HistoryIndexNode.of(v.obj).serialize()
        // When attributes are empty, no trailing field is added — deserialization returns empty map
        val restored = HistoryIndexNode.deserialize(serialized, graph)
        assertEquals(AttributesAggregation.EMPTY, restored.attributes)
    }

    @Test
    fun `attributes with separator characters are round-tripped safely`() {
        // Keys and values that contain every Separators character must survive urlEncode/urlDecode
        val v = createInitialVersion(
            mapOf(
                "key/with/slashes" to "val,with,commas",
                "key=with=equals" to "val;with;semicolons",
            ),
        )
        val graph = v.graph
        val node = HistoryIndexNode.of(v.obj)
        val serialized = node.serialize()
        val deserialized = HistoryIndexNode.deserialize(serialized, graph)
        assertEquals(node.attributes, deserialized.attributes)
    }
}
