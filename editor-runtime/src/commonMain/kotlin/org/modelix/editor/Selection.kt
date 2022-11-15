package org.modelix.editor

abstract class Selection : IProducesHtml {
    abstract fun processKeyDown(event: JSKeyboardEvent): Boolean
}

abstract class SelectionView<E : Selection>(val selection: E) {
    abstract fun updateBounds()
}