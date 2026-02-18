import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.UnclassifiedReferenceLinkReference
import kotlin.test.Test
import kotlin.test.assertEquals

class ReferenceSerializationTests {

    @Test
    fun deserializePNodeReferenceOldFormat() {
        assertEquals(
            PNodeReference(0xabcd1234, "2bfd9f5e-95d0-11ee-b9d1-0242ac120002"),
            PNodeReference.deserialize("pnode:abcd1234@2bfd9f5e-95d0-11ee-b9d1-0242ac120002"),
        )
    }

    @Test
    fun deserializePNodeReferenceNewFormat() {
        assertEquals(
            PNodeReference(0xabcd1234, "2bfd9f5e-95d0-11ee-b9d1-0242ac120002"),
            PNodeReference.deserialize("modelix:2bfd9f5e-95d0-11ee-b9d1-0242ac120002/abcd1234"),
        )
    }

    @Test
    fun decodeUnclassifiedReferenceLinkReference() {
        val unclassified = UnclassifiedReferenceLinkReference(
            "2bca1aa3-c113-4542-8ac2-2a6a30636981/6006699537885399164/6006699537885399165",
        )

        assertEquals(
            IRoleReference.decodeStringFromLegacyApi(unclassified.stringForLegacyApi(), IReferenceLinkReference),
            IReferenceLinkReference.fromId("2bca1aa3-c113-4542-8ac2-2a6a30636981/6006699537885399164/6006699537885399165"),
        )
    }
}
