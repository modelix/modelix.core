package org.modelix.editor

import org.w3c.dom.HTMLElement

class JSCodeCompletionMenuUI(val ccmenu: CodeCompletionMenu, val editor: JsEditorComponent) {
    fun updateBounds() {
        val ccContainerElement = GeneratedHtmlMap.getOutput(ccmenu) ?: return
        val layoutable = ccmenu.anchor
        val anchorElement = GeneratedHtmlMap.getOutput(layoutable) ?: return
        val anchorAbsoluteBounds = anchorElement.getAbsoluteBounds()
        val anchorRelativeBounds =
            anchorAbsoluteBounds.relativeTo(editor.getMainLayer()?.getAbsoluteBounds() ?: ZERO_BOUNDS)
        val patternElement = ccContainerElement.descendants().filterIsInstance<HTMLElement>()
            .first { it.classList.contains("ccmenu-pattern") }
        val left: Double = when (ccmenu.completionPosition) {
            CompletionPosition.CENTER -> anchorRelativeBounds.x
            CompletionPosition.LEFT -> {
                anchorRelativeBounds.x - patternElement.getAbsoluteBounds().width
            }

            CompletionPosition.RIGHT -> anchorRelativeBounds.maxX()
        }
        ccContainerElement.style.left = "${left}px"
        ccContainerElement.style.top = "${anchorRelativeBounds.y}px"

        val caretElement =
            ccContainerElement.descendants().filterIsInstance<HTMLElement>().first { it.classList.contains("caret") }
        JSCaretSelectionView.updateCaretBounds(
            patternElement,
            ccmenu.patternEditor.caretPos,
            ccContainerElement,
            caretElement
        )

        ccContainerElement.descendants().filterIsInstance<HTMLElement>()
            .firstOrNull { it.classList.contains("ccSelectedEntry") }
            ?.scrollIntoView(js("""{block: "nearest"}"""))
    }
}