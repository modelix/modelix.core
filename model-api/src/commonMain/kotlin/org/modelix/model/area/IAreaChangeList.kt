package org.modelix.model.area

interface IAreaChangeList {
    fun visitChanges(visitor: (IAreaChangeEvent) -> Boolean)
}
