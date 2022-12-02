package org.modelix.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class TextLayouterTest {

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
        assertEquals(expected, buildCells(template).layout.toString())
    }
}