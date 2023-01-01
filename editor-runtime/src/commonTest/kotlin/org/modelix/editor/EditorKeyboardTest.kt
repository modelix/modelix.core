package org.modelix.editor

import kotlin.test.Test
import kotlin.test.assertEquals

class EditorKeyboardTest {
    @Test
    fun arrowLeft() {
        val rootCell = EditorTestUtils.buildCells(listOf(listOf("111"), listOf(EditorTestUtils.indentChildren, "222", listOf(EditorTestUtils.newLine, listOf("333")), listOf(listOf("444"), "555")), EditorTestUtils.newLine, "666", "777", "888"))
        val editor = EditorComponent(engine = null) { rootCell }
        val findByText: (String)->LayoutableCell = { text -> rootCell.descendants().find { it.getVisibleText() == text }!!.layoutable()!! }
        val layoutable444 = findByText("444")
        editor.changeSelection(CaretSelection(layoutable444, 2))
        assertEquals(CaretSelection(layoutable444, 2), editor.getSelection())

        testCaretChange(editor, KnownKeys.ArrowLeft, "444", 1)
        testCaretChange(editor, KnownKeys.ArrowLeft, "444", 0)
        testCaretChange(editor, KnownKeys.ArrowLeft, "333", 3)
        testCaretChange(editor, KnownKeys.ArrowLeft, "333", 2)
        testCaretChange(editor, KnownKeys.ArrowLeft, "333", 1)
        testCaretChange(editor, KnownKeys.ArrowLeft, "333", 0)
        testCaretChange(editor, KnownKeys.ArrowLeft, "222", 3)
        testCaretChange(editor, KnownKeys.ArrowLeft, "222", 2)
        testCaretChange(editor, KnownKeys.ArrowLeft, "222", 1)
        testCaretChange(editor, KnownKeys.ArrowLeft, "222", 0)
        testCaretChange(editor, KnownKeys.ArrowLeft, "111", 3)
        testCaretChange(editor, KnownKeys.ArrowLeft, "111", 2)
        testCaretChange(editor, KnownKeys.ArrowLeft, "111", 1)
        testCaretChange(editor, KnownKeys.ArrowLeft, "111", 0)
        testCaretChange(editor, KnownKeys.ArrowLeft, "111", 0) // don't move at the beginning
    }

    @Test
    fun arrowRight() {
        val rootCell = EditorTestUtils.buildCells(listOf("111", "222", EditorTestUtils.newLine, "333", "444", "555", EditorTestUtils.newLine, "666", "777", "888"))
        val editor = EditorComponent(engine = null) { rootCell }
        val findByText: (String)->LayoutableCell = { text -> rootCell.descendants().find { it.getVisibleText() == text }!!.layoutable()!! }
        val layoutable444 = findByText("444")
        editor.changeSelection(CaretSelection(layoutable444, 2))
        assertEquals(CaretSelection(layoutable444, 2), editor.getSelection())

        testCaretChange(editor, KnownKeys.ArrowRight, "444", 3)
        testCaretChange(editor, KnownKeys.ArrowRight, "555", 0)
        testCaretChange(editor, KnownKeys.ArrowRight, "555", 1)
        testCaretChange(editor, KnownKeys.ArrowRight, "555", 2)
        testCaretChange(editor, KnownKeys.ArrowRight, "555", 3)
        testCaretChange(editor, KnownKeys.ArrowRight, "666", 0)
        testCaretChange(editor, KnownKeys.ArrowRight, "666", 1)
        testCaretChange(editor, KnownKeys.ArrowRight, "666", 2)
        testCaretChange(editor, KnownKeys.ArrowRight, "666", 3)
        testCaretChange(editor, KnownKeys.ArrowRight, "777", 0)
        testCaretChange(editor, KnownKeys.ArrowRight, "777", 1)
        testCaretChange(editor, KnownKeys.ArrowRight, "777", 2)
        testCaretChange(editor, KnownKeys.ArrowRight, "777", 3)
        testCaretChange(editor, KnownKeys.ArrowRight, "888", 0)
        testCaretChange(editor, KnownKeys.ArrowRight, "888", 1)
        testCaretChange(editor, KnownKeys.ArrowRight, "888", 2)
        testCaretChange(editor, KnownKeys.ArrowRight, "888", 3)
        testCaretChange(editor, KnownKeys.ArrowRight, "888", 3) // don't move at the end
    }

    private fun testCaretChange(editor: EditorComponent, key: KnownKeys, expectedCellText: String, expectedPosition: Int) {
        editor.processKeyDown(JSKeyboardEvent(key))
        val layoutable = editor.getRootCell().descendants().find { it.getVisibleText() == expectedCellText }!!.layoutable()!!
        assertEquals(CaretSelection(layoutable, expectedPosition), editor.getSelection())
    }
}