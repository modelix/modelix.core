package org.modelix.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class TextLayouterTest {
    private val noSpace = Any()
    private val newLine = Any()
    private val emptyLine = Any()

    @Test
    fun testNoSpace() {
        val textA = TextLayouter().apply {
            append(LayoutableWord("a"))
        }.done()
        assertEquals("a", textA.toString())

        val textB = TextLayouter().apply {
            append(LayoutableWord("b"))
        }.done()
        assertEquals("b", textB.toString())

        val textC = TextLayouter().apply {
            noSpace()
            append(LayoutableWord("c"))
        }.done()
        assertEquals("c", textC.toString())

        val textD = TextLayouter().apply {
            noSpace()
            append(LayoutableWord("d"))
        }.done()
        assertEquals("d", textD.toString())

        val textAB = TextLayouter().apply {
            append(textA)
            noSpace()
            append(textB)
        }.done()
        assertEquals("ab", textAB.toString())

        val textABB = TextLayouter().apply {
            append(textAB)
            noSpace()
            append(textB)
        }.done()
        assertEquals("abb", textABB.toString())

        val textAB_B = TextLayouter().apply {
            append(textAB)
            append(textB)
        }.done()
        assertEquals("ab b", textAB_B.toString())

        val textABBC = TextLayouter().apply {
            append(textABB)
            append(textC)
        }.done()
        assertEquals("abbc", textABBC.toString())

        val textCC = TextLayouter().apply {
            append(textC)
            append(textC)
        }.done()
        assertEquals("cc", textCC.toString())

        val textABBCC = TextLayouter().apply {
            append(textABB)
            append(textCC)
        }.done()
        assertEquals("abbcc", textABBCC.toString())

        val textDD = TextLayouter().apply {
            append(textD)
            append(textD)
        }.done()
        assertEquals("dd", textDD.toString())

        val textCCDD = TextLayouter().apply {
            append(textCC)
            append(textDD)
        }.done()
        assertEquals("ccdd", textCCDD.toString())

        val textABBCCDD = TextLayouter().apply {
            append(textABB)
            append(textCCDD)
        }.done()
        assertEquals("abbccdd", textABBCCDD.toString())
    }

    @Test fun cells1() = testCells("ab c", listOf(listOf("a", noSpace, "b"), "c"))
    @Test fun cells2() = testCells("a bc", listOf(listOf("a", "b"), noSpace, "c"))
    @Test fun cells3() = testCells("a bc", listOf(listOf("a", "b"), noSpace, "c"))
    @Test fun cells4() = testCells("ab", listOf("a", listOf(noSpace, "b")))
    @Test fun cells5() = testCells("a\nb", listOf("a", listOf(newLine, "b")))
    @Test fun cells6() = testCells("ab", listOf(listOf("a", noSpace), "b"))
    @Test fun cells7() = testCells("a\nb", listOf(listOf("a", newLine), "b"))

    fun testCells(expected: String, template: Any) {
        assertEquals(expected, buildCells(template).layout.toString())
    }

    fun buildCells(template: Any): Cell {
        return when (template) {
            noSpace -> Cell(CellData().apply { properties[CommonCellProperties.noSpace] = true })
            newLine -> Cell(CellData().apply { properties[CommonCellProperties.onNewLine] = true })
            is String -> Cell(TextCellData(template, ""))
            is List<*> -> Cell(CellData()).apply { template.forEach { addChild(buildCells(it!!)) } }
            else -> throw IllegalArgumentException("Unsupported: $template")
        }
    }
}