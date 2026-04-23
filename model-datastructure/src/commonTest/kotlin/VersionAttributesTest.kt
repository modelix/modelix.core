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

    @Test
    fun `separator characters in keys and values survive round trip`() {
        val graph1 = FullyLoadedObjectGraph()
        val attrs = mapOf(
            "key/with/slashes" to "val,with,commas",
            "key=with=equals" to "val;with;semicolons",
            "key%encoded" to "val%encoded",
            "unicode-key-\u00e9\u4e2d\u6587" to "unicode-val-\u00e9\u4e2d\u6587",
        )
        val originalVersion: IVersion = IVersion.builder()
            .tree(IModelTree.builder().graph(graph1).build())
            .also { builder -> attrs.forEach { (k, v) -> builder.attribute(k, v) } }
            .build()

        assertEquals(attrs, originalVersion.getAttributes())

        val graph2 = FullyLoadedObjectGraph()
        val restoredVersion = graph2.loadObjects(
            rootHash = originalVersion.getObjectHash(),
            rootDeserializer = CPVersion,
            receivedObjects = originalVersion.asObject().getDescendantsAndSelf().toMap({ it.getHash() }, { it.data.serialize() }).getBlocking(graph1),
        ).let { CLVersion(it) }

        assertEquals(attrs, restoredVersion.getAttributes())
    }
}
