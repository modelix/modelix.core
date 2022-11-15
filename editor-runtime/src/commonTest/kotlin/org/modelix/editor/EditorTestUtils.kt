package org.modelix.editor

val noSpace = Any()
val newLine = Any()
val indentChildren = Any()

fun buildCells(template: Any): Cell {
    return when (template) {
        noSpace -> Cell(CellData().apply { properties[CommonCellProperties.noSpace] = true })
        newLine -> Cell(CellData().apply { properties[CommonCellProperties.onNewLine] = true })
        is String -> Cell(TextCellData(template, ""))
        is List<*> -> Cell(CellData()).apply {
            template.forEach { child ->
                when (child) {
                    indentChildren -> data.properties[CommonCellProperties.indentChildren] = true
                    is ECellLayout -> data.properties[CommonCellProperties.layout] = child
                    else -> addChild(buildCells(child!!))
                }
            }
        }
        else -> throw IllegalArgumentException("Unsupported: $template")
    }
}