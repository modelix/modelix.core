package org.modelix.editor

import kotlinx.html.*
import org.modelix.incremental.IncrementalList

class TextLine(words_: Iterable<Layoutable>) : IProducesHtml {
    var initialText: LayoutedText? = null
    var finalText: LayoutedText? = null
    val words: List<Layoutable> = words_.toList()
    val layoutablesIndexList: IncrementalList<Pair<Cell, LayoutableCell>> =
        IncrementalList.of(words.filterIsInstance<LayoutableCell>().map { it.cell to it })

    init {
        words.filter { it.initialLine == null }.forEach { it.initialLine = this }
        words.forEach { it.finalLine = this }
    }

    fun getY(): Int {
        val text = getText() ?: return 0
        return text.lines.asSequence().indexOf(this)
    }

    fun getText(): LayoutedText? = finalText?.rootText() ?: initialText?.rootText()

    fun getSibling(next: Boolean): TextLine? {
        val text = getText() ?: return null
        val index = text.lines.indexOf(this)
        if (index < 0) return null
        val siblingIndex = index + (if (next) 1 else -1)
        if (siblingIndex < 0 || siblingIndex >= text.lines.size) return null
        return text.lines[siblingIndex]
    }

    fun getContextIndent() = initialText?.getContextIndent() ?: 0

    override fun <T> produceHtml(consumer: TagConsumer<T>) {
        consumer.div("line") {
            words.forEach { element: Layoutable ->
                produceChild(element)
            }
            if (words.sumOf { it.getLength() } == 0) {
                +Typography.nbsp.toString()
            }
        }
    }
}

class LayoutedText(
    val lines: TreeList<TextLine>,
    val beginsWithNewLine: Boolean,
    val endsWithNewLine: Boolean,
    val beginsWithNoSpace: Boolean,
    val endsWithNoSpace: Boolean,
    var indent: Int = 0
) : IProducesHtml {
    var owner: LayoutedText? = null
    val layoutablesIndexList: IncrementalList<Pair<Cell, LayoutableCell>> =
        IncrementalList.concat(lines.map { it.layoutablesIndexList })

    init {
        lines.forEach { if (it.initialText == null) it.initialText = this }
        lines.forEach { it.finalText = this }
    }

    fun rootText(): LayoutedText? = owner?.rootText() ?: owner ?: this

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

    override fun <T> produceHtml(consumer: TagConsumer<T>) {
        consumer.div("layouted-text") {
            lines.forEach { line ->
                produceChild(line)
            }
        }
    }
}

class TextLayouter {
    private var beginsWithNewLine: Boolean = false
    private var beginsWithNoSpace: Boolean = false
    private val closedLines = ArrayList<TreeList<TextLine>>()
    private var reusableLastLine: TextLine? = null
    private var lastLine: MutableList<Layoutable>? = null
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
            if (line.toList() == reusableLastLine?.words?.toList()) {
                closedLines.add(TreeList.of(reusableLastLine!!))
            } else {
                closedLines.add(TreeList.of(TextLine(line)))
            }

            lastLine = null
            insertNewLineNext = false
            autoInsertSpace = true
        }
    }

    private fun addNewLine() {
        closeLine()
        lastLine = ArrayList()
    }

    private fun ensureLastLine(): MutableList<Layoutable> {
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
            if (lastLineToCopy.words.isNotEmpty()) {
                lastLineToCopy.words.forEachIndexed { index, it ->
                    if (index > 0) noSpace() // already contains LayoutableSpace instances
                    append(it)
                }
            }
            reusableLastLine = lastLineToCopy
        }

        if (text.endsWithNoSpace) noSpace()
        if (text.endsWithNewLine) onNewLine()
    }

    fun append(element: Layoutable) {
        reusableLastLine = null
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

abstract class Layoutable : IProducesHtml {
    var initialLine: TextLine? = null
    var finalLine: TextLine? = null

    abstract fun getLength(): Int
    abstract fun isWhitespace(): Boolean
    abstract fun toText(): String
    override fun toString(): String = toText()

    fun getX(): Int {
        val line = getLine() ?: return 0
        val prevSiblings = line.words.takeWhile { it != this }
        return prevSiblings.sumOf { it.getLength() }
    }

    fun getY(): Int = getLine()?.getY() ?: 0

    fun getLine(): TextLine? = finalLine ?: initialLine

    fun getSiblingInLine(next: Boolean): Layoutable? {
        val line = getLine() ?: return null
        val index = line.words.indexOf(this)
        if (index < 0) return null
        val siblingIndex = index + (if (next) + 1 else -1)
        if (siblingIndex < 0 || siblingIndex >= line.words.size) return null
        return line.words[siblingIndex]
    }

    fun getSiblingInText(next: Boolean): Layoutable? {
        val siblingInLine = getSiblingInLine(next)
        if (siblingInLine != null) return siblingInLine
        val siblingLines = generateSequence(getLine()) { it.getSibling(next) }.drop(1)
        val nonEmptySiblingLine = siblingLines.filter { it.words.isNotEmpty() }.firstOrNull() ?: return null
        return if (next) nonEmptySiblingLine.words.first() else nonEmptySiblingLine.words.last()
    }

    fun getSiblingsInText(next: Boolean): Sequence<Layoutable> {
        return generateSequence(getSiblingInText(next)) { it.getSiblingInText(next) }
    }
}

/*class LayoutableWord(val text: String) : ILayoutable {
    override fun getLength(): Int = text.length
    override fun isWhitespace(): Boolean = text.isNotEmpty() && text.last().isWhitespace()
    override fun toText(): String = text
    override fun toHtml(consumer: TagConsumer<*>) {
        consumer.onTagContent(text.useNbsp())
    }
}*/
class LayoutableCell(val cell: Cell) : Layoutable() {
    init {
        require(cell.data is TextCellData) { "Not a text cell: $cell" }
    }
    override fun getLength(): Int {
        return toText().length
    }
    override fun toText(): String {
        return cell.getProperty(CommonCellProperties.textReplacement)
            ?: (cell.data as TextCellData).getVisibleText(cell)
    }
    override fun isWhitespace(): Boolean = false
    override fun <T> produceHtml(consumer: TagConsumer<T>) {
        val textIsOverridden = cell.getProperty(CommonCellProperties.textReplacement) != null
        val isPlaceholder = (cell.data as TextCellData).text.isEmpty()
        val textColor = when {
            textIsOverridden -> "#A81E1E"
            isPlaceholder -> cell.getProperty(CommonCellProperties.placeholderTextColor)
            else -> cell.getProperty(CommonCellProperties.textColor)
        }
        val backgroundColor = when {
            textIsOverridden -> "rgba(255, 0, 0, 0.5)"
            else -> null
        }
        consumer.span("text-cell") {
            val styleParts = mutableListOf<String>()
            if (textColor != null) styleParts += "color: $textColor"
            if (backgroundColor != null) styleParts += "background-color: $backgroundColor"
            if (styleParts.isNotEmpty()) style = styleParts.joinToString(";")

            +toText().useNbsp()
        }
    }
}

fun Cell.layoutable(): LayoutableCell? {
    //return rootCell().layout.lines.asSequence().flatMap { it.words }.filterIsInstance<LayoutableCell>().find { it.cell == this }
    return editorComponent?.resolveLayoutable(this)
}

class LayoutableIndent(val indentSize: Int): Layoutable() {
    fun totalIndent() = indentSize + (initialLine?.getContextIndent() ?: 0)
    override fun getLength(): Int = totalIndent() * 2
    override fun isWhitespace(): Boolean = true
    override fun toText(): String = (1..totalIndent()).joinToString("") { "  " }
    override fun <T> produceHtml(consumer: TagConsumer<T>) {
        consumer.span("indent") {
            +toText().useNbsp()
        }
    }
}
class LayoutableSpace(): Layoutable() {
    override fun getLength(): Int = 1
    override fun isWhitespace(): Boolean = true
    override fun toText(): String = " "
    override fun <T> produceHtml(consumer: TagConsumer<T>) {
        consumer.span {
            +Typography.nbsp.toString()
        }
    }
}

fun String.useNbsp() = replace(' ', Typography.nbsp)