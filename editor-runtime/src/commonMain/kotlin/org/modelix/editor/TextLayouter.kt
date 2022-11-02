package org.modelix.editor

import kotlinx.html.*

class LayoutedText : Freezable() {
    private val lines: MutableList<MutableList<ILayoutable>> = mutableListOf(ArrayList())

    fun addLine() {
        checkNotFrozen()
        lines.add(ArrayList())
    }

    fun isLastLineEmpty() = lines.isEmpty() || lines.last().isEmpty()

    fun addElement(element: ILayoutable) {
        checkNotFrozen()
        if (lines.isEmpty()) addLine()
        lines.last().add(element)
    }

    fun getLastElement(): ILayoutable? = lines.lastOrNull()?.lastOrNull()

    fun copy() : LayoutedText {
        return LayoutedText().also { copy -> copy.lines.addAll(lines.map { line -> ArrayList(line) }) }
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

class TextLayouter {
    private val text = LayoutedText()
    private var indent: Int = 0
    private var autoInsertSpace: Boolean = true
    private var insertNewLineNext: Boolean = false

    fun onNewLine() {
        insertNewLineNext = true
    }
    fun emptyLine() {
        onNewLine()
        text.addLine()
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
            text.addLine()
        }
        if (indent > 0 && text.isLastLineEmpty()) {
            text.addElement(LayoutableIndent(indent))
        }
        val lastOnLine = text.getLastElement()
        if (autoInsertSpace && lastOnLine != null && !lastOnLine.isWhitespace()) {
            text.addElement(LayoutableWord(" "))
        }
        text.addElement(element)
        autoInsertSpace = true
    }

    fun close() : LayoutedText {
        text.freeze()
        return text
    }
}

interface ILayoutable {
    fun getLength(): Int
    fun isWhitespace(): Boolean
    fun toText(): String
    fun toHtml(consumer: TagConsumer<*>)
}

class LayoutableWord(val text: String) : ILayoutable {
    override fun getLength(): Int = text.length
    override fun isWhitespace(): Boolean = text.isNotEmpty() && text.last().isWhitespace()
    override fun toText(): String = text
    override fun toHtml(consumer: TagConsumer<*>) {
        consumer.onTagContent(text.useNbsp())
    }
}
class LayoutableCell(val cell: Cell) : ILayoutable {
    init {
        require(cell.data is TextCellData) { "Not a text cell: $cell" }
    }
    override fun getLength(): Int {
        return toText().length
    }
    override fun toText(): String = (cell.data as TextCellData).getVisibleText(cell)
    override fun isWhitespace(): Boolean = false
    override fun toHtml(consumer: TagConsumer<*>) {
        val textColor = cell.getProperty(CommonCellProperties.textColor)
        consumer.span("text-cell") {
            if (textColor != null) {
                style = "color:$textColor"
            }
            +toText().useNbsp()
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