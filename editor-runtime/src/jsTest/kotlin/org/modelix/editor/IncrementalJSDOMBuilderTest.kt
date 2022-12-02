package org.modelix.editor

import kotlinx.browser.document
import kotlinx.html.TagConsumer
import org.w3c.dom.HTMLElement
import org.w3c.dom.Text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class IncrementalJSDOMBuilderTest {
    @Test
    fun test() {
        if (js("typeof document === 'undefined'")) {
            // there is no document on nodejs
            js("require('jsdom-global')()")
        }
        val textCellToChange = Cell(TextCellData("b"))
        val cell = Cell(CellData()).apply {
            addChild(Cell(TextCellData("a")))
            addChild(Cell(CellData()).apply {
                addChild(textCellToChange)
                addChild(Cell(CellData().also { it.properties[CommonCellProperties.onNewLine] = true }))
                addChild(Cell(TextCellData("c")))
            })
            addChild(Cell(TextCellData("d")))
        }

        var domBuilder: TagConsumer<HTMLElement> = IncrementalJSDOMBuilder(document, null)
        val dom = cell.layout.toHtml(domBuilder)
        val elements1 = listOf(dom) + dom.descendants()
        println(cell)
        println(dom.outerHTML)

        val newText = "X"
        val cell2 = replaceCell(cell, textCellToChange, Cell(TextCellData(newText)))
        assertNotSame(cell, cell2, "No cell was replaced")
        domBuilder = IncrementalJSDOMBuilder(document, dom)
        val dom2 = cell2.layout.toHtml(domBuilder)
        val elements2 = listOf(dom2) + dom2.descendants()
        println(cell2)
        println(dom2.outerHTML)
        assertEquals(elements1.size, elements2.size)

        val expectedChanges = elements1.indices.joinToString("") {
            val element2 = elements2[it]
            if (element2 is Text && element2.textContent == newText) "C" else "-"
        }
        println(expectedChanges)
        assertTrue(expectedChanges.contains("C"))
        val actualChanges = elements1.indices.joinToString("") { if (elements1[it] === elements2[it]) "-" else "C" }
        println(actualChanges)
        assertEquals(expectedChanges, actualChanges)
    }

    fun replaceCell(tree: Cell, oldCell: Cell, newCell: Cell): Cell {
        if (tree == oldCell) return newCell
        val oldChildren = tree.getChildren()
        val newChildren = oldChildren.map { replaceCell(it, oldCell, newCell) }
        if (oldChildren != newChildren) {
            return Cell(tree.data).also { newParent -> newChildren.forEach {
                it.parent?.removeChild(it)
                newParent.addChild(it)
            } }
        }
        return tree
    }
}