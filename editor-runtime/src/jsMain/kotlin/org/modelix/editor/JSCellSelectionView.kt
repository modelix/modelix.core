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
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.style
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList

class JSCellSelectionView(selection: CellSelection, val editor: JsEditorComponent) : SelectionView<CellSelection>(selection) {

    override fun update() {
        val mainLayerBounds = editor.getMainLayer()?.getAbsoluteBounds() ?: ZERO_BOUNDS
        val selectionDom = GeneratedHtmlMap.getOutput(this) ?: return
        val lines: Map<TextLine, List<Layoutable>> = selection.getLayoutables().groupBy { it.getLine()!! }
        val lineSelectionDoms = selectionDom.childNodes.asList().filterIsInstance<HTMLElement>()

        val applyBounds = ArrayList<() -> Unit>()

        var selectionBounds: Bounds? = null
        for ((words, lineSelectionDom) in lines.values.zip(lineSelectionDoms)) {
            val wordSelectionDoms = lineSelectionDom.childNodes.asList().filterIsInstance<HTMLElement>()
            var lineBounds: Bounds? = null
            for ((word, wordSelectionDom) in words.zip(wordSelectionDoms)) {
                val wordDom = GeneratedHtmlMap.getOutput(word) ?: continue
                val wordBounds = wordDom.getAbsoluteBounds().relativeTo(mainLayerBounds)
                lineBounds = lineBounds.union(wordBounds)
                applyBounds += {
                    with(wordSelectionDom.style) {
                        position = "absolute"
                        left = "${wordBounds.x - lineBounds!!.x}px"
                        top = "${wordBounds.y - lineBounds.y}px"
                        width = "${wordBounds.width}px"
                        height = "${wordBounds.height}px"
                    }
                }
            }
            selectionBounds = selectionBounds.union(lineBounds)
            applyBounds += {
                if (lineBounds != null) {
                    with(lineSelectionDom.style) {
                        position = "absolute"
                        left = "${lineBounds.x - selectionBounds!!.x}px"
                        top = "${lineBounds.y - selectionBounds.y}px"
                        width = "${lineBounds.width}px"
                        height = "${lineBounds.height}px"
                    }
                }
            }
        }
        applyBounds += {
            if (selectionBounds != null) {
                with(selectionDom.style) {
                    position = "absolute"
                    left = "${selectionBounds.x}px"
                    top = "${selectionBounds.y}px"
                    width = "${selectionBounds.width}px"
                    height = "${selectionBounds.height}px"
                }
            }
        }
        applyBounds.forEach { it() }
    }

    override fun <T> produceHtml(consumer: TagConsumer<T>) {
        consumer.div("cell-selection own") {
            val lines: Map<TextLine, List<Layoutable>> = selection.getLayoutables().groupBy { it.getLine()!! }
            for (line in lines) {
                div("selected-line") {
                    for (word in line.value) {
                        span("selected-word") {
                            style = "background-color:hsla(196, 67%, 45%, 0.3)"
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun updateCaretBounds(textElement: HTMLElement, caretPos: Int, coordinatesElement: HTMLElement?, caretElement: HTMLElement) {
            val text = textElement.innerText
            val textLength = text.length
            val cellAbsoluteBounds = textElement.getAbsoluteInnerBounds()
            val cellRelativeBounds = cellAbsoluteBounds.relativeTo(coordinatesElement?.getAbsoluteBounds() ?: ZERO_BOUNDS)
            val characterWidth = if (textLength == 0) 0.0 else cellAbsoluteBounds.width / textLength
            val caretX = cellRelativeBounds.x + caretPos * characterWidth
            val leftEnd = caretPos == 0
            val rightEnd = caretPos == textLength
            val caretOffsetX = if (rightEnd && !leftEnd) -4 else -1
            val caretOffsetY = if (leftEnd || rightEnd) -1 else 0
            caretElement.style.height = "${cellRelativeBounds.height}px"
            caretElement.style.left = "${caretX + caretOffsetX}px"
            caretElement.style.top = "${cellRelativeBounds.y + caretOffsetY}px"
        }
    }
}