package org.modelix.editor

import kotlinx.html.*

class TextLine(words_: Iterable<ILayoutable>) {
    var owner: LayoutedText? = null
    val words: List<ILayoutable> = words_.toList()

    init {
        words.filterIsInstance<LayoutableIndent>().filter { it.owner == null }.forEach { it.owner = this }
    }

    fun getContextIndent() = owner?.getContextIndent() ?: 0
}

class LayoutedText(
    val lines: TreeList<TextLine>,
    val beginsWithNewLine: Boolean,
    val endsWithNewLine: Boolean,
    val beginsWithNoSpace: Boolean,
    val endsWithNoSpace: Boolean,
    var indent: Int = 0
) {
    var owner: LayoutedText? = null

    init {
        lines.forEach { if (it.owner == null) it.owner = this }
    }

    fun getContextIndent(): Int = (owner?.getContextIndent() ?: 0) + indent

    override fun toString(): String {
        val buffer = StringBuilder()
        lines.forEachIndexed { index, line ->
            if (index != 0) buffer.append('\n')
            line.words.forEach { element ->
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
                    line.words.forEach { element: ILayoutable ->
                        element.toHtml(tagConsumer)
                    }
                    if (line.words.sumOf { it.getLength() } == 0) {
                        +Typography.nbsp.toString()
                    }
                }
            }
        }
    }
}

class TextLayouter {
    private var beginsWithNewLine: Boolean = false
    private var beginsWithNoSpace: Boolean = false
    private val closedLines = ArrayList<TreeList<TextLine>>()
    private var lastLine: MutableList<ILayoutable>? = null
    private var currentIndent: Int = 0
    private var autoInsertSpace: Boolean = true
    private var insertNewLineNext: Boolean = false
    private val childTexts = ArrayList<LayoutedText>()

    fun done(): LayoutedText {
        val endsWithNoSpace = !autoInsertSpace
        val endsWithNewLine = insertNewLineNext
        closeLine()
        val newText = LayoutedText(
            TreeList.flatten(closedLines),
            beginsWithNewLine = beginsWithNewLine,
            endsWithNewLine = endsWithNewLine,
            beginsWithNoSpace = beginsWithNoSpace,
            endsWithNoSpace = endsWithNoSpace
        )
        childTexts.forEach { it.owner = newText }
        return newText
    }

    private fun closeLine() {
        lastLine?.let { line ->
            if (line.first() !is LayoutableIndent) line.add(0, LayoutableIndent(currentIndent))
            closedLines.add(TreeList.of(TextLine(line)))
            lastLine = null
            insertNewLineNext = false
            autoInsertSpace = true
        }
    }

    private fun addNewLine() {
        closeLine()
        lastLine = ArrayList()
    }

    private fun ensureLastLine(): MutableList<ILayoutable> {
        if (lastLine == null) {
            lastLine = ArrayList()
        }
        return lastLine!!
    }

    fun isEmpty() = closedLines.isEmpty() && lastLine == null

    fun onNewLine() {
        if (isEmpty()) beginsWithNewLine = true
        insertNewLineNext = true
    }
    fun emptyLine() {
        addNewLine()
        onNewLine()
    }
    fun withIndent(body: ()->Unit) {
        val oldIndent = currentIndent
        try {
            currentIndent++
            body()
        } finally {
            currentIndent = oldIndent
        }
    }
    fun noSpace() {
        if (isEmpty()) beginsWithNoSpace = true
        autoInsertSpace = false
    }

    fun append(text: LayoutedText) {
        childTexts.add(text)
        text.indent = currentIndent
        if (text.beginsWithNoSpace) noSpace()
        var closedLinesToCopy = text.lines
        if (text.beginsWithNewLine || insertNewLineNext || lastLine == null) {
            closeLine()
        } else {
            val line = closedLinesToCopy.first()
            closedLinesToCopy = closedLinesToCopy.withoutFirst()
            if (line != null && line.words.isNotEmpty()) {
                line.words.filter { it !is LayoutableIndent }.forEachIndexed { index, it ->
                    if (index > 0) noSpace() // already contains LayoutableSpace instances
                    append(it)
                }
            }
        }

        var lastLineToCopy: TextLine? = null
        if (!text.endsWithNewLine) {
            lastLineToCopy = closedLinesToCopy.last()
            closedLinesToCopy = closedLinesToCopy.withoutLast()
        }
        if (closedLinesToCopy.isNotEmpty()) {
            closeLine()
            closedLines.add(closedLinesToCopy)
        }

        if (lastLineToCopy != null) {
            if (lastLineToCopy != null && lastLineToCopy.words.isNotEmpty()) {
                lastLineToCopy.words.forEachIndexed { index, it ->
                    if (index > 0) noSpace() // already contains LayoutableSpace instances
                    append(it)
                }
            }
        }

        if (text.endsWithNoSpace) noSpace()
        if (text.endsWithNewLine) onNewLine()
    }

    fun append(element: ILayoutable) {
        ensureLastLine()
        if (insertNewLineNext) {
            insertNewLineNext = false
            if (lastLine!!.isNotEmpty()) {
                addNewLine()
            }
        }
        if (currentIndent > 0 && lastLine!!.isEmpty()) {
            //lastLine!!.add(LayoutableIndent(currentIndent))
        }
        val lastOnLine = lastLine!!.lastOrNull()
        if (autoInsertSpace && lastOnLine != null && !lastOnLine.isWhitespace() && element !is LayoutableSpace) {
            lastLine!!.add(LayoutableSpace())
        }
        lastLine!!.add(element)
        autoInsertSpace = true
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
    var owner: TextLine? = null
    fun totalIndent() = indentSize + (owner?.getContextIndent() ?: 0)
    override fun getLength(): Int = totalIndent() * 2
    override fun isWhitespace(): Boolean = true
    override fun toText(): String = (1..totalIndent()).joinToString { "  " }
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