package org.modelix.editor

class LayoutedCells {
    private val lines: MutableList<MutableList<ILayoutable>> = mutableListOf(ArrayList())
    private var indent: String = ""
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
            indent += "  "
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
        if (indent.isNotEmpty() && (lines.isEmpty() || lines.last().isEmpty())) {
            lines.last().add(LayoutableText(indent))
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

    fun toHtml(): String {
        val buffer = StringBuilder()
        buffer.append("<div>\n")
        lines.forEach { line ->
            buffer.append("""    <div class="line">""")
            val oldLength = buffer.length
            line.forEach { element ->
                element.toHtml(buffer)
            }
            if (buffer.length == oldLength) {
                buffer.append("&nbsp;")
            }
            buffer.append("</div>\n")
        }
        buffer.append("</div>\n")
        return buffer.toString()
    }
}

interface ILayoutable {
    fun getLength(): Int
    fun isWhitespace(): Boolean
    fun toText(): String
    fun toHtml(buffer: Appendable)
}

class LayoutableText(val text: String) : ILayoutable {
    override fun getLength(): Int = text.length
    override fun isWhitespace(): Boolean = text.isNotEmpty() && text.last().isWhitespace()
    override fun toText(): String = text
    override fun toHtml(buffer: Appendable) {
        buffer.escapeAppend(text)
    }
}
class LayoutableCell(val cell: TextCell) : ILayoutable {
    override fun getLength(): Int {
        return cell.getVisibleText().length
    }
    override fun toText(): String = cell.getVisibleText()
    override fun isWhitespace(): Boolean = false
    override fun toHtml(buffer: Appendable) {
        val textColor = cell.getProperty(CommonCellProperties.textColor)
        if (textColor != null) {
            buffer.apply {
                append("""<span style="color:""")
                append(textColor)
                append("""">""")
                escapeAppend(cell.getVisibleText())
                append("""</span>""")
            }
        } else {
            buffer.escapeAppend(cell.getVisibleText())
        }
    }
}
class LayoutableIndent(val indentSize: Int): ILayoutable {
    override fun getLength(): Int = indentSize * 2
    override fun isWhitespace(): Boolean = true
    override fun toText(): String = (1..indentSize).joinToString { "  " }
    override fun toHtml(buffer: Appendable) {
        buffer.append("<span>${toText().replace(" ", "&nbsp;")}</span>")
    }
}
class LayoutableSpace(): ILayoutable {
    override fun getLength(): Int = 1
    override fun isWhitespace(): Boolean = true
    override fun toText(): String = " "
    override fun toHtml(buffer: Appendable) {
        buffer.append("<span>&nbsp;</span>")
    }
}

private val escapeMap = mapOf(
    '<' to "&lt;",
    '>' to "&gt;",
    '&' to "&amp;",
    '\"' to "&quot;",
    ' ' to "&nbsp;"
).let { mappings ->
    val maxCode = mappings.keys.map { it.toInt() }.maxOrNull() ?: -1

    Array(maxCode + 1) { mappings[it.toChar()] }
}

private fun Appendable.escapeAppend(s: CharSequence) {
    var lastIndex = 0
    val mappings = escapeMap
    val size = mappings.size

    for (idx in 0..s.length - 1) {
        val ch = s[idx].toInt()
        if (ch < 0 || ch >= size) continue
        val escape = mappings[ch]
        if (escape != null) {
            append(s.substring(lastIndex, idx))
            append(escape)
            lastIndex = idx + 1
        }
    }

    if (lastIndex < s.length) {
        append(s.substring(lastIndex, s.length))
    }
}

private fun escape(s: CharSequence): String {
    return StringBuilder().apply { escapeAppend(s) }.toString()
}