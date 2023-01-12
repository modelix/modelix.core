package org.modelix.editor

abstract class Selection : IKeyboardHandler {
    abstract fun isValid(): Boolean
    abstract fun update(editor: EditorComponent): Selection?
}

abstract class SelectionView<E : Selection>(val selection: E) : IProducesHtml {
    abstract fun update()
}