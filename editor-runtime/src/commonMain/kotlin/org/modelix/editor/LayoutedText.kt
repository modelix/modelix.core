package org.modelix.editor

class LayoutedText {
    private var indent: String = ""
    private var autoInsertSpace: Boolean = true
    private val buffer = StringBuilder()
    private var insertNewLineNext: Boolean = true

    fun onNewLine() {
        insertNewLineNext = true
    }
    fun emptyLine() {
        onNewLine()
        buffer.append('\n')
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
    fun append(text: String) {
        if (insertNewLineNext) {
            insertNewLineNext = false
            buffer.append('\n')
        }
        if (buffer.isEmpty() || buffer.last() == '\n') {
            buffer.append(indent)
        }
        if (autoInsertSpace && buffer.isNotEmpty() && !buffer.last().isWhitespace()) {
            buffer.append(' ')
        }
        buffer.append(text)
        autoInsertSpace = true
    }

    override fun toString(): String {
        return buffer.toString()
    }
}