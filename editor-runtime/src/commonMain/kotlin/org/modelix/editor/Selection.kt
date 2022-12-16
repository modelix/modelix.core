package org.modelix.editor

abstract class Selection : IProducesHtml {
    abstract fun processKeyDown(event: JSKeyboardEvent): Boolean
    abstract fun isValid(): Boolean
    abstract fun update(editor: EditorComponent): Selection?
}

abstract class SelectionView<E : Selection>(val selection: E) {
    abstract fun updateBounds()
}