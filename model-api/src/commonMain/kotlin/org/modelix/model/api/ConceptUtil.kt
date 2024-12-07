package org.modelix.model.api

/**
 * Returns this concept and all of its super-concepts.
 *
 * @return list of this concept and all its super-concepts
 */
fun IConcept.getAllConcepts(): List<IConcept> {
    val acc = LinkedHashSet<IConcept>()
    collectConcepts(this, acc)
    return acc.toList()
}

private fun collectConcepts(concept: IConcept, acc: MutableSet<IConcept>) {
    if (acc.contains(concept)) return
    acc.add(concept)
    for (superConcept in concept.getDirectSuperConcepts()) {
        collectConcepts(superConcept, acc)
    }
}
