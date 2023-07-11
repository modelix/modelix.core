package org.modelix.model.sync

class ImportStats {
    val additions: List<Addition> = mutableListOf()
    val deletions: List<Deletion> = mutableListOf()
    val moves: List<Move> = mutableListOf()
    val propertyChanges: List<PropertyChange> = mutableListOf()
    val referenceChanges: List<ReferenceChange> = mutableListOf()

    fun addAddition(nodeId: String?, parentId: String?, role: String?, index: Int) {
        (additions as MutableList).add(Addition(nodeId, parentId, role, index))
    }

    fun addDeletion(nodeId: String?, parentId: String?, role: String?, descendantIds: List<String>) {
       (deletions as MutableList).add(Deletion(nodeId, parentId, role, descendantIds))
    }

    fun addMove(
        nodeId: String?,
        oldParentId: String?,
        oldRole: String?,
        oldIndex: Int,
        newParentId: String?,
        newRole: String?,
        newIndex: Int
    ) {
        (moves as MutableList).add(
            Move(nodeId, oldParentId, oldRole, oldIndex, newParentId, newRole, newIndex))
    }

    fun addPropertyChange(nodeId: String, property: String) {
        (propertyChanges as MutableList).add(PropertyChange(nodeId, property))
    }

    fun addReferenceChange(nodeId: String, reference: String) {
        (referenceChanges as MutableList).add(ReferenceChange(nodeId, reference))
    }

    fun getTotal() : Int = additions.size + deletions.size + moves.size + propertyChanges.size + referenceChanges.size

}

interface NodeChange {
    val nodeId: String?
    val parentId: String?
    val role: String?
}

data class Addition(
    override val nodeId: String?,
    override val parentId: String?,
    override val role: String?,
    val index: Int
) : NodeChange

data class Deletion(
    override val nodeId: String?,
    override val parentId: String?,
    override val role: String?,
    val descendantIds: List<String>
) : NodeChange

data class Move(
    override val nodeId: String?,
    override val parentId: String?,
    override val role: String?,
    val oldIndex: Int,
    val newParentId: String?,
    val newRole: String?,
    val newIndex: Int
) : NodeChange

data class PropertyChange(val nodeId: String, val property: String)
data class ReferenceChange(val nodeId: String, val reference: String)

