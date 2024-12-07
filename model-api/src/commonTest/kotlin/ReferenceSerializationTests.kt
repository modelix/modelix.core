import org.modelix.model.api.PNodeReference
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
}
