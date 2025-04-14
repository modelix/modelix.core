import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.objects.FullyLoadedObjectGraph
import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.model.IVersion
import org.modelix.model.lazy.CLVersion
import org.modelix.model.persistent.CPVersion
import org.modelix.streams.getBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class VersionAttributesTest {

    @Test
    fun `serialize and deserialize version with attributes`() {
        val graph1 = FullyLoadedObjectGraph()

        val key = "git-commit!&?*#+-ID"
        val value = "a16add525cbe04329708f83dbe7057e69bb1922d"

        IModelTree.builder().graph(graph1).build()
        val originalVersion: IVersion = IVersion.builder()
            .tree(IModelTree.builder().graph(graph1).build())
            .attribute(key, value)
            .build()

        assertEquals(mapOf(key to value), originalVersion.getAttributes())

        val graph2 = FullyLoadedObjectGraph()
        val restoredVersion = graph2.loadObjects(
            rootHash = originalVersion.getObjectHash(),
            rootDeserializer = CPVersion,
            receivedObjects = originalVersion.asObject().getDescendantsAndSelf().toMap({ it.getHash() }, { it.data.serialize() }).getBlocking(graph1),
        ).let { CLVersion(it) }

        assertEquals(mapOf(key to value), restoredVersion.getAttributes())
    }
}
