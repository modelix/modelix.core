package org.modelix.editor

class JSCodeCompletionMenuUI(val ccmenu: CodeCompletionMenu, val editor: JsEditorComponent) {
    fun updateBounds() {
        val dom = GeneratedHtmlMap.getOutput(ccmenu) ?: return
        val layoutable = ccmenu.anchor
        val htmlElement = GeneratedHtmlMap.getOutput(layoutable) ?: return
        val cellAbsoluteBounds = htmlElement.getAbsoluteBounds()
        val cellRelativeBounds = cellAbsoluteBounds.relativeTo(editor.getMainLayer()?.getAbsoluteBounds() ?: ZERO_BOUNDS)
        dom.style.left = "${cellRelativeBounds.x}px"
        dom.style.top = "${cellRelativeBounds.y + cellRelativeBounds.height}px"
    }
}