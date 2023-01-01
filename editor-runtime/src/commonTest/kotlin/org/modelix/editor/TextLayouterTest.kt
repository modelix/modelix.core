package org.modelix.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TextLayouterTest {
    val noSpace = EditorTestUtils.noSpace
    val newLine = EditorTestUtils.newLine
    val indentChildren = EditorTestUtils.indentChildren

    @Test fun space1() = testCells("ab c", listOf(listOf("a", noSpace, "b"), "c"))
    @Test fun space2() = testCells("a bc", listOf(listOf("a", "b"), noSpace, "c"))
    @Test fun space3() = testCells("a bc", listOf(listOf("a", "b"), noSpace, "c"))
    @Test fun space4() = testCells("ab", listOf("a", listOf(noSpace, "b")))
    @Test fun space5() = testCells("ab", listOf(listOf("a", noSpace), "b"))

    @Test fun newLine1() = testCells("a\nb", listOf("a", listOf(newLine, "b")))
    @Test fun newLine2() = testCells("a\nb", listOf(listOf("a", newLine), "b"))
    @Test fun newLine3() = testCells("a {\nb\n}", listOf(listOf(listOf("a"), listOf("{", newLine, listOf("b"), newLine, "}"))))

    @Test fun indent1() = testCells("{\n  b\n  c\n  d\n}", listOf("{", newLine, listOf(indentChildren, "b", newLine, "c", newLine, "d"), newLine, "}"))
    @Test fun indent2() = testCells("{\n  b\n  c\n  d\n}", listOf("{", newLine, listOf(indentChildren, "b", newLine, "c", newLine, "d", newLine), "}"))
    @Test fun indent3() = testCells("  {\n  b\n  c\n  d\n  }", listOf(indentChildren, "{", newLine, "b", newLine, "c", newLine, "d", newLine, "}"))

    @Test fun indent4() = testCells("a {\n  b\n  c\n  d\n}", listOf("a", listOf("{", newLine, listOf(indentChildren, "b", newLine, "c", newLine, "d"), newLine, "}")))
    @Test fun indent5() = testCells("a {\n  b\n  c\n  d\n}", listOf("a", listOf("{", newLine, listOf(indentChildren, "b", newLine, "c", newLine, "d", newLine), "}")))
    @Test fun indent6() = testCells("a {\n  b\n  c\n  d\n  }", listOf("a", listOf(indentChildren, "{", newLine, "b", newLine, "c", newLine, "d", newLine, "}")))

    private fun testCells(expected: String, template: Any) {
        val text = EditorTestUtils.buildCells(template).layout
        text.lines.forEach { line ->
            assertSame(text, line.getText())
            line.words.forEach { word ->
                assertSame(line, word.getLine())
            }
        }
        assertEquals(expected, text.toString())
    }
}