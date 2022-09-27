package org.modelix.editor

import kotlinx.html.*

class LayoutedCells {
    private val lines: MutableList<MutableList<ILayoutable>> = mutableListOf(ArrayList())
    private var indent: Int = 0
    private var autoInsertSpace: Boolean = true
    private var insertNewLineNext: Boolean = false

    fun onNewLine() {
        insertNewLineNext = true
    }
    fun emptyLine() {
        onNewLine()
        lines.add(ArrayList())
    }
    fun withIndent(body: ()->Unit) {
        val oldIndent = indent
        try {
            indent++
            body()
        } finally {
            indent = oldIndent
        }
    }
    fun noSpace() {
        autoInsertSpace = false
    }
    fun append(element: ILayoutable) {
        if (insertNewLineNext) {
            insertNewLineNext = false
            lines.add(ArrayList())
        }
        if (indent > 0 && (lines.isEmpty() || lines.last().isEmpty())) {
            lines.last().add(LayoutableIndent(indent))
        }
        val lastOnLine = lines.last().lastOrNull()
        if (autoInsertSpace && lastOnLine != null && !lastOnLine.isWhitespace()) {
            lines.last().add(LayoutableText(" "))
        }
        lines.last().add(element)
        autoInsertSpace = true
    }

    override fun toString(): String {
        val buffer = StringBuilder()
        lines.forEachIndexed { index, line ->
            if (index != 0) buffer.append('\n')
            line.forEach { element ->
                buffer.append(element.toText())
            }
        }
        return buffer.toString()
    }

    fun toHtml(tagConsumer: TagConsumer<*>) {
        tagConsumer.div {
            lines.forEach { line ->
                div("line") {
                    val parentTag = this
                    line.forEach { element: ILayoutable ->
                        element.toHtml(tagConsumer)
                    }
                    if (line.sumOf { it.getLength() } == 0) {
                        +Typography.nbsp.toString()
                    }
                }
            }
        }
    }
}

interface ILayoutable {
    fun getLength(): Int
    fun isWhitespace(): Boolean
    fun toText(): String
    fun toHtml(consumer: TagConsumer<*>)
}

class LayoutableText(val text: String) : ILayoutable {
    override fun getLength(): Int = text.length
    override fun isWhitespace(): Boolean = text.isNotEmpty() && text.last().isWhitespace()
    override fun toText(): String = text
    override fun toHtml(consumer: TagConsumer<*>) {
        consumer.onTagContent(text.useNbsp())
    }
}
class LayoutableCell(val cell: TextCell) : ILayoutable {
    override fun getLength(): Int {
        return cell.getVisibleText().length
    }
    override fun toText(): String = cell.getVisibleText()
    override fun isWhitespace(): Boolean = false
    override fun toHtml(consumer: TagConsumer<*>) {
        val textColor = cell.getProperty(CommonCellProperties.textColor)
        consumer.span("text-cell") {
            if (textColor != null) {
                style = "color:$textColor"
            }
            +cell.getVisibleText().useNbsp()
        }
    }
}
class LayoutableIndent(val indentSize: Int): ILayoutable {
    override fun getLength(): Int = indentSize * 2
    override fun isWhitespace(): Boolean = true
    override fun toText(): String = (1..indentSize).joinToString { "  " }
    override fun toHtml(consumer: TagConsumer<*>) {
        consumer.span("indent") {
            +toText().useNbsp()
        }
    }
}
class LayoutableSpace(): ILayoutable {
    override fun getLength(): Int = 1
    override fun isWhitespace(): Boolean = true
    override fun toText(): String = " "
    override fun toHtml(consumer: TagConsumer<*>) {
        consumer.span {
            +Typography.nbsp.toString()
        }
    }
}

fun String.useNbsp() = replace(' ', Typography.nbsp)