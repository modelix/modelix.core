package org.modelix.editor

import kotlinx.html.TagConsumer
import kotlinx.html.div

open class EditorComponent {

    var rootCell: Cell? = null
    var selection: Selection? = null

    fun toHtml(tagConsumer: TagConsumer<*>) {
        tagConsumer.div("editor") {
            div("main-layer") {
                val layoutedCell = LayoutedCells()
                rootCell?.layout(layoutedCell)
                layoutedCell.toHtml(tagConsumer)
            }
            div("selection-layer") {
                selection?.toHtml(tagConsumer)
            }
        }
    }

}