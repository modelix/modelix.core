import org.modelix.model.api.ITreeChangeVisitorEx

class TreeChangeCollector : ITreeChangeVisitorEx {
    val events: MutableList<ChangeEvent> = ArrayList()

    override fun containmentChanged(nodeId: Long) {
        events += ContainmentChangedEvent(nodeId)
    }

    override fun conceptChanged(nodeId: Long) {
        events += ConceptChangedEvent(nodeId)
    }

    override fun childrenChanged(nodeId: Long, role: String?) {
        events += ChildrenChangedEvent(nodeId, role)
    }

    override fun referenceChanged(nodeId: Long, role: String) {
        events += ReferenceChangedEvent(nodeId, role)
    }

    override fun propertyChanged(nodeId: Long, role: String) {
        events += PropertyChangedEvent(nodeId, role)
    }

    override fun nodeRemoved(nodeId: Long) {
        events += NodeRemovedEvent(nodeId)
    }

    override fun nodeAdded(nodeId: Long) {
        events += NodeAddedEvent(nodeId)
    }

    abstract class ChangeEvent
    data class ContainmentChangedEvent(val nodeId: Long) : ChangeEvent()
    data class ConceptChangedEvent(val nodeId: Long) : ChangeEvent()
    data class ChildrenChangedEvent(val nodeId: Long, val role: String?) : ChangeEvent()
    data class ReferenceChangedEvent(val nodeId: Long, val role: String?) : ChangeEvent()
    data class PropertyChangedEvent(val nodeId: Long, val role: String?) : ChangeEvent()
    data class NodeRemovedEvent(val nodeId: Long) : ChangeEvent()
    data class NodeAddedEvent(val nodeId: Long) : ChangeEvent()
}
