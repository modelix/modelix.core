package org.modelix.editor

import org.modelix.incremental.IncrementalList

interface IFreezable {
    fun freeze()
    fun checkNotFrozen()
}

open class Freezable : IFreezable {
    private var frozen: Boolean = false
    override fun freeze() {
        frozen = true
    }

    fun isFrozen() = frozen

    override fun checkNotFrozen() {
        if (frozen) {
            throw IllegalStateException("Cell cannot be modified anymore")
        }
    }
}

class Cell(val data: CellData = CellData()) : Freezable() {
    private var editorComponentValue: EditorComponent? = null
    var parent: Cell? = null
    private val children: MutableList<Cell> = ArrayList()
    val layout: LayoutedText by lazy(LazyThreadSafetyMode.NONE) {
        TextLayouter().also { data.layout(it, this) }.done()
    }
    val referencesIndexList: IncrementalList<Pair<CellReference, Cell>> by lazy(LazyThreadSafetyMode.NONE) {
        IncrementalList.concat(
            IncrementalList.of(data.cellReferences.map { it to this }),
            IncrementalList.concat(children.map { it.referencesIndexList })
        )
    }
    var editorComponent: EditorComponent?
        get() = editorComponentValue ?: parent?.editorComponent
        set(value) {
            if (value != null && parent != null) throw IllegalStateException("Only allowed on the root cell")
            editorComponentValue = value
        }

    override fun freeze() {
        if (isFrozen()) return
        super.freeze()
        data.freeze()
        children.forEach { it.freeze() }
    }

    override fun toString(): String {
        return data.cellToString(this)
    }

    fun addChild(child: Cell) {
        require(child.parent == null) { "$child already has a parent ${child.parent}" }
        children.add(child)
        child.parent = this
    }

    fun removeChild(child: Cell) {
        require(child.parent == this) { "$child is not a child of $this" }
        children.remove(child)
        child.parent = null
    }

    fun getChildren(): List<Cell> = children

    fun <T> getProperty(key: CellPropertyKey<T>): T {
        return if (key.inherits && !data.properties.isSet(key)) {
            parent.let { if (it != null) it.getProperty(key) else key.defaultValue }
        } else {
            data.properties[key]
        }
    }

    fun rootCell(): Cell = parent?.rootCell() ?: this
}

fun Cell.getVisibleText(): String? = (data as? TextCellData)?.getVisibleText(this)
fun Cell.getSelectableText(): String? = (data as? TextCellData)?.text
