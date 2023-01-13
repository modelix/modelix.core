/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.editor

import kotlinx.html.TagConsumer
import kotlinx.html.classes
import kotlinx.html.div
import kotlinx.html.style
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import kotlin.math.max
import kotlin.math.min

class JSCaretSelectionView(selection: CaretSelection, val editor: JsEditorComponent) : SelectionView<CaretSelection>(selection) {

    private fun hasRange() = selection.start != selection.end

    override fun <T> produceHtml(consumer: TagConsumer<T>) {
        with(consumer) {
            div("caret-selection") {
                style = "position: absolute"
                if (hasRange()) {
                    div("selected-word") {
                        style = "position: absolute; background-color:hsla(196, 67%, 45%, 0.3)"
                    }
                }
                div("caret own") {
                    style = "position: absolute"
                    val textLength = selection.layoutable.cell.getVisibleText()?.length ?: 0
                    if (textLength == 0) {
                        // A typical case is a StringLiteral editor for an empty string.
                        // There is no space around the empty text cell.
                        // 'leftend' or 'rightend' styles would look like the caret is set into one of the '"' cells.
                    } else if (selection.end == 0) {
                        classes += "leftend"
                    } else if (selection.end == textLength) {
                        classes += "rightend"
                    }
                }
            }
        }
    }

    override fun update() {
        val textDom = GeneratedHtmlMap.getOutput(selection.layoutable) ?: return
        val mainLayerBounds = editor.getMainLayer()?.getAbsoluteBounds() ?: ZERO_BOUNDS
        val textBoundsUtil = TextBoundsUtil(textDom)
        val selectionDom = GeneratedHtmlMap.getOutput(this) ?: return
        val selectionBounds = textBoundsUtil.getTextBounds().expanded(1.0)
        selectionDom.setBounds(selectionBounds.relativeTo(mainLayerBounds))
        val caretDom = selectionDom.childNodes.asList().filterIsInstance<HTMLElement>().lastOrNull() ?: return
        updateCaretBounds(textDom, selection.end, selectionBounds, caretDom)

        if (hasRange()) {
            val rangeDom = selectionDom.childNodes.asList().filterIsInstance<HTMLElement>().firstOrNull() ?: return
            val minPos = min(selection.start, selection.end)
            val maxPos = max(selection.start, selection.end)
            val substringBounds = textBoundsUtil.getSubstringBounds(minPos until maxPos)
            rangeDom.setBounds(substringBounds.relativeTo(selectionBounds))
        }
    }

    companion object {
        fun updateCaretBounds(textElement: HTMLElement, caretPos: Int, coordinatesElement: HTMLElement?, caretDom: HTMLElement) {
            updateCaretBounds(textElement, caretPos, coordinatesElement?.getAbsoluteBounds() ?: ZERO_BOUNDS, caretDom)
        }

        fun updateCaretBounds(textElement: HTMLElement, caretPos: Int, relativeTo: Bounds, caretDom: HTMLElement) {
            val textBoundsUtil = TextBoundsUtil(textElement, relativeTo)
            val textBounds = textBoundsUtil.getTextBounds()
            val text = textBoundsUtil.getText()
            val leftEnd = caretPos == 0
            val rightEnd = caretPos == text.length
            val caretOffsetX = if (rightEnd && !leftEnd) -4 else -1
            val caretOffsetY = if (leftEnd || rightEnd) -1 else 0
            caretDom.style.height = "${textBounds.height}px"
            caretDom.style.left = "${textBoundsUtil.getCaretX(caretPos) + caretOffsetX}px"
            caretDom.style.top = "${textBounds.y + caretOffsetY}px"
        }
    }
}

private class TextBoundsUtil(val dom: HTMLElement, val relativeTo: Bounds = ZERO_BOUNDS) {
    fun getText(): String = dom.innerText
    fun getTextLength() = getText().length
    fun getTextBounds() = dom.getAbsoluteInnerBounds().relativeTo(relativeTo)
    fun getTextWidth() = getTextBounds().width
    fun getTextHeight() = getTextBounds().height
    fun getCharWidth() = getTextWidth() / getTextLength()
    fun getCaretX(pos: Int) = getTextBounds().let {
        val charWidth = it.width / getTextLength()
        it.x + pos * charWidth
    }
    fun getSubstringBounds(range: IntRange) = getTextBounds().let {
        val charWidth = it.width / getTextLength()
        val minX = it.x + range.first * charWidth
        val maxX = it.x + (range.last + 1) * charWidth
        it.copy(x = minX, width = maxX - minX)
    }
}