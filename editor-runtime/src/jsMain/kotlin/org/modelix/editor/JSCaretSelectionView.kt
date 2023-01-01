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

class JSCaretSelectionView(selection: CaretSelection, val editor: JsEditorComponent) : SelectionView<CaretSelection>(selection) {

    override fun updateBounds() {
        val dom = GeneratedHtmlMap.getOutput(selection) ?: return
        val layoutable = selection.layoutable
        val htmlElement = GeneratedHtmlMap.getOutput(layoutable) ?: return
        val text = htmlElement.innerText
        val cellAbsoluteBounds = htmlElement.getAbsoluteBounds()
        val cellRelativeBounds = cellAbsoluteBounds.relativeTo(editor.getMainLayer()?.getAbsoluteBounds() ?: ZERO_BOUNDS)
        val characterWidth = cellAbsoluteBounds.width / text.length
        val caretPos = selection.end
        val caretX = cellAbsoluteBounds.x + caretPos * characterWidth
        val leftEnd = caretPos == 0
        val rightEnd = caretPos == text.length
        val caretOffsetX = if (rightEnd) -5 else -2
        val caretOffsetY = if (leftEnd || rightEnd) -1 else 0
        dom.style.height = "${cellRelativeBounds.height}px"
        dom.style.left = "${caretX + caretOffsetX}px"
        dom.style.top = "${cellRelativeBounds.y + caretOffsetY}px"
    }
}