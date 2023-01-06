package org.modelix.editor

import kotlinx.browser.document
import kotlinx.html.TagConsumer
import org.w3c.dom.HTMLElement
import org.w3c.dom.Text
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class IncrementalJSDOMBuilderTest {
    init {
        if (js("typeof document === 'undefined'")) {
            // there is no document on nodejs
            js("require('jsdom-global')()")
        }
    }

    @Test
    fun test() {
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
        val oldTreeStr = tree.toString()
        if (tree == oldCell) return newCell
        val oldChildren = tree.getChildren()
        val newChildren = oldChildren.map { replaceCell(it, oldCell, newCell) }
        if (oldChildren != newChildren) {
            val newTree = Cell(tree.data).also { newParent ->
                newChildren.forEach {
                    it.parent?.removeChild(it)
                    newParent.addChild(it)
                }
            }
            val newTreeStr = newTree.toString()
            return newTree
        }
        return tree
    }

    fun insertCell(tree: Cell, anchor: Cell, newCell: Cell): Cell {
        val oldTreeStr = tree.toString()
        if (tree == anchor) return Cell().also { newParent ->
            newParent.addChild(newCell)
            anchor.parent?.removeChild(anchor)
            newParent.addChild(anchor)
        }
        val oldChildren = tree.getChildren()
        val newChildren = oldChildren.map { insertCell(it, anchor, newCell) }
        if (oldChildren != newChildren) {
            val newTree = Cell(tree.data).also { newParent ->
                newChildren.forEach {
                    it.parent?.removeChild(it)
                    newParent.addChild(it)
                }
            }
            val newTreeStr = newTree.toString()
            return newTree
        }
        return tree
    }

    @Test fun runRandomTest_4_3() = runRandomTests(567454, 4, 3)
    @Test fun runRandomTest_1_1() = runRandomTests(567454, 1, 1)
    @Test fun runRandomTest_1_2() = runRandomTests(567454, 1, 2)
    @Test fun runRandomTest_1_3() = runRandomTests(567454, 1, 3)
    @Test fun runRandomTest_2_1() = runRandomTests(567454, 2, 1)
    @Test fun runRandomTest_2_2() = runRandomTests(567454, 2, 2)
    @Test fun runRandomTest_2_3() = runRandomTests(567454, 2, 3)
    @Test fun runRandomTest_3_1() = runRandomTests(567454, 3, 1)
    @Test fun runRandomTest_3_2() = runRandomTests(567454, 3, 2)
    @Test fun runRandomTest_3_3() = runRandomTests(567454, 3, 3)
    @Test fun runRandomTest_5_4_567462() = runRandomTests(567462, 5, 4)
    @Ignore
    @Test fun runRandomTest_5_4() {
        for (seed in 1..10) {
            println("test ${567454 + seed}, 5, 4")
            runRandomTests(567454 + seed, 5, 4)
        }
    }

    fun runRandomTests(seed: Int, cellsPerLevel: Int, levels: Int) {
        val rand = Random(seed)
        runRandomTest(rand, cellsPerLevel, levels) { cell ->
            val randomLeafCell = cell.descendants().filter { !it.getVisibleText().isNullOrEmpty() }.shuffled(rand).firstOrNull()
                ?: cell.descendants().filter { it.getChildren().isEmpty() }.shuffled(rand).first()
            println("replace $randomLeafCell")
            replaceCell(
                cell,
                randomLeafCell,
                Cell(TextCellData("replacement"))
            )
        }
        runRandomTest(rand, cellsPerLevel, levels) { cell ->
            val randomCell = cell.descendants().shuffled(rand).firstOrNull()
                ?: cell.descendants().filter { it.getChildren().isEmpty() }.shuffled(rand).first()
            println("insertBefore $randomCell")
            insertCell(
                cell,
                randomCell,
                Cell(TextCellData("insertion"))
            )
        }
    }

    fun runRandomTest(rand: Random, cellsPerLevel: Int, levels: Int, modify: (Cell) -> Cell) {
        val cell = EditorTestUtils.buildRandomCells(rand, cellsPerLevel, levels)
        val dom = cell.layout.toHtml(IncrementalJSDOMBuilder(document, null))
        val html = dom.outerHTML
        println("old html: " + html)
        println("old cells: $cell")
        val newCell = modify(cell)
        println("new cells: $newCell")
        val dom2incremental = newCell.layout.toHtml(IncrementalJSDOMBuilder(document, dom))
        val html2incremental = dom2incremental.outerHTML

        newCell.descendantsAndSelf().forEach { it.clearCachedLayout() }
        val dom2nonIncremental = newCell.layout.toHtml(IncrementalJSDOMBuilder(document, null))
        val html2nonIncremental = dom2nonIncremental.outerHTML
        assertEquals(html2nonIncremental, html2incremental)
    }
}