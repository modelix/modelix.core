package org.modelix.editor

class LayoutedText {
    private var indent: String = ""
    private var autoInsertSpace: Boolean = true
    private val buffer = StringBuilder()

    fun onNewLine() {
        if (buffer.length != 0 && buffer.last() != '\n') {
            buffer.append('\n')
        }
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
        if (buffer.length == 0 || buffer.last() == '\n') {
            buffer.append(indent)
        }
        if (autoInsertSpace && buffer.length != 0 && !buffer.last().isWhitespace()) {
            buffer.append(' ')
        }
        buffer.append(text)
        autoInsertSpace = true
    }

    override fun toString(): String {
        return buffer.toString()
    }
}