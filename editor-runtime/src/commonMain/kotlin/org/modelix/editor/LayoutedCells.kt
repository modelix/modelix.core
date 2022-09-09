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
}

interface ILayoutable {
    fun getLength(): Int
    fun isWhitespace(): Boolean
    fun toText(): String
}

class LayoutableText(val text: String) : ILayoutable {
    override fun getLength(): Int = text.length
    override fun isWhitespace(): Boolean = text.isNotEmpty() && text.last().isWhitespace()
    override fun toText(): String = text
}
class LayoutableCell(val cell: TextCell) : ILayoutable {
    override fun getLength(): Int {
        return cell.getVisibleText().length
    }

    override fun toText(): String = cell.getVisibleText()

    override fun isWhitespace(): Boolean = false
}