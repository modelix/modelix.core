import org.modelix.model.api.ITreeChangeVisitorEx
import kotlin.test.fail

class FailingVisitor : ITreeChangeVisitorEx {
    override fun containmentChanged(nodeId: Long) {
        fail("Tree expected to be the same. Changed containment in node $nodeId")
    }

    override fun conceptChanged(nodeId: Long) {
        fail("Tree expected to be the same. Changed concept in node $nodeId")
    }

    override fun childrenChanged(nodeId: Long, role: String?) {
        fail("Tree expected to be the same. Changed children in node $nodeId in role $role")
    }

    override fun referenceChanged(nodeId: Long, role: String) {
        fail("Tree expected to be the same. Changed reference in node $nodeId in role $role")
    }

    override fun propertyChanged(nodeId: Long, role: String) {
        fail("Tree expected to be the same. Changed property in node $nodeId in role $role")
    }

    override fun nodeRemoved(nodeId: Long) {
        fail("Tree expected to be the same. Node $nodeId was removed.")
    }

    override fun nodeAdded(nodeId: Long) {
        fail("Tree expected to be the same. Node $nodeId was added.")
    }
}
