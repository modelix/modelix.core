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

import org.modelix.model.api.INode

data class CaretPositionPolicy(
    private val avoidedCellRefs: Set<CellReference>,
    private val preferredCellRefs: Set<CellReference>
) {
    constructor(preferredCellRef: CellReference) : this(emptySet(), setOf(preferredCellRef))
    constructor(preferredNode: INode) : this(NodeCellReference(preferredNode.reference))

    fun prefer(cellReference: CellReference) = copy(preferredCellRefs = preferredCellRefs + cellReference)
    fun avoid(cellReference: CellReference) = copy(avoidedCellRefs = avoidedCellRefs + cellReference)

    fun merge(other: CaretPositionPolicy) = CaretPositionPolicy(
        avoidedCellRefs + other.avoidedCellRefs,
        preferredCellRefs + other.preferredCellRefs
    )

    fun getBestSelection(editor: EditorComponent): CaretSelection? {
        val candidates = preferredCellRefs
            .flatMap { editor.resolveCell(it) }
            .flatMap { it.descendantsAndSelf() }
            .mapNotNull { editor.resolveLayoutable(it) }

        val best = candidates
            .sortedByDescending { it.cell.isTabTarget() }
            .sortedBy { it.cell.ancestors(true).filter { isAvoided(it) }.count() }
            .firstOrNull() ?: return null

        return CaretSelection(best, (best.cell.getSelectableText() ?: "").length)
    }

    private fun isAvoided(cell: Cell) = cell.data.cellReferences.intersect(avoidedCellRefs).isNotEmpty()
}

enum class CaretPositionType {
    START,
    END
}