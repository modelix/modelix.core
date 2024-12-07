import org.modelix.model.api.ConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.async.ContainmentCycleException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ContainmentCycleTest : TreeTestBase() {
    @Test
    fun containment_cycle_is_prevented() {
        assertFailsWith(exceptionClass = ContainmentCycleException::class) {
            initialTree
                .addNewChild(ITree.ROOT_ID, "roleA", -1, 100, null as ConceptReference?)
                .addNewChild(100, "roleB", -1, 101, null as ConceptReference?)
                .moveChild(101, "roleC", -1, 100)
        }
    }
}
