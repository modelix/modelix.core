import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.objects.FullyLoadedObjectGraph
import org.modelix.model.IVersion
import org.modelix.model.VersionMerger
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLVersion
import org.modelix.model.operations.OTBranch
import kotlin.test.Test
import kotlin.test.assertEquals

class VersionMergerAttributesTest {

    private val idGenerator = IdGenerator.newInstance(1)

    private fun createBaseVersion(): CLVersion {
        val graph = FullyLoadedObjectGraph()
        val tree = IGenericModelTree.builder()
            .withInt64Ids()
            .graph(graph)
            .build()
        return IVersion.builder().tree(tree).build() as CLVersion
    }

    private fun makeChild(base: CLVersion, attrs: Map<String, String> = emptyMap()): CLVersion {
        val branch = OTBranch(PBranch(base.getTree(), idGenerator), idGenerator)
        branch.runWriteT { t ->
            t.addNewChild(ITree.ROOT_ID, "child", -1, null as ConceptReference?)
        }
        val (ops, newTree) = branch.getPendingChanges()
        return CLVersion.builder()
            .regularUpdate(base)
            .tree(newTree)
            .operations(ops.map { it.getOriginalOp() })
            .also { builder -> attrs.forEach { (k, v) -> builder.attribute(k, v) } }
            .buildLegacy()
    }

    @Test
    fun `merged version carries right-side attributes when left has none`() {
        val base = createBaseVersion()

        val left = makeChild(base)
        val right = makeChild(base, mapOf("env" to "ci", "run" to "42"))

        val merged = VersionMerger().mergeChange(left, right)

        assertEquals(mapOf("env" to "ci", "run" to "42"), merged.getAttributes())
    }

    @Test
    fun `merged version unions attributes from both sides`() {
        val base = createBaseVersion()

        val left = makeChild(base, mapOf("env" to "staging", "team" to "platform"))
        val right = makeChild(base, mapOf("run" to "42"))

        val merged = VersionMerger().mergeChange(left, right)

        assertEquals(mapOf("env" to "staging", "team" to "platform", "run" to "42"), merged.getAttributes())
    }

    @Test
    fun `merged version right-side wins on conflicting keys`() {
        val base = createBaseVersion()

        val left = makeChild(base, mapOf("env" to "staging"))
        val right = makeChild(base, mapOf("env" to "ci", "run" to "42"))

        val merged = VersionMerger().mergeChange(left, right)

        assertEquals(mapOf("env" to "ci", "run" to "42"), merged.getAttributes())
    }

    @Test
    fun `merged version carries left-side attributes when right has none`() {
        val base = createBaseVersion()

        val left = makeChild(base, mapOf("env" to "staging"))
        val right = makeChild(base)

        val merged = VersionMerger().mergeChange(left, right)

        assertEquals(mapOf("env" to "staging"), merged.getAttributes())
    }

    @Test
    fun `merged version has empty attributes when neither side has any`() {
        val base = createBaseVersion()

        val left = makeChild(base)
        val right = makeChild(base)

        val merged = VersionMerger().mergeChange(left, right)

        assertEquals(emptyMap(), merged.getAttributes())
    }
}
