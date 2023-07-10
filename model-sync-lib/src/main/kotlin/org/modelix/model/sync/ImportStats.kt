package org.modelix.model.sync

class ImportStats {
    val additions: List<String> = mutableListOf()
    val deletions: List<String> = mutableListOf()
    val moves: List<String> = mutableListOf()
    val propertyChanges: List<PropertyChange> = mutableListOf()
    val referenceChanges: List<ReferenceChange> = mutableListOf()

    fun addAddition(nodeId: String) {
        (additions as MutableList).add(nodeId)
    }

    fun addDeletion(nodeId: String) {
       (deletions as MutableList).add(nodeId)
    }

    fun addMove(nodeId: String) {
        (moves as MutableList).add(nodeId)
    }

    fun addPropertyChange(nodeId: String, property: String) {
        (propertyChanges as MutableList).add(PropertyChange(nodeId, property))
    }

    fun addReferenceChange(nodeId: String, reference: String) {
        (referenceChanges as MutableList).add(ReferenceChange(nodeId, reference))
    }

}

data class PropertyChange(val nodeId: String, val property: String)
data class ReferenceChange(val nodeId: String, val reference: String)

