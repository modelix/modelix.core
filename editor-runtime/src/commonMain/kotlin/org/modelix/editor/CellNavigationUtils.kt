/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.editor

fun Cell.previousLeafs(includeSelf: Boolean = false): Sequence<Cell> {
    return generateSequence(this) { it.previousLeaf() }.drop(if (includeSelf) 0 else 1)
}

fun Cell.nextLeafs(includeSelf: Boolean = false): Sequence<Cell> {
    return generateSequence(this) { it.nextLeaf() }.drop(if (includeSelf) 0 else 1)
}

fun Cell.previousLeaf(condition: (Cell)->Boolean): Cell? {
    return previousLeafs(false).find(condition)
}

fun Cell.nextLeaf(condition: (Cell)->Boolean): Cell? {
    return nextLeafs(false).find(condition)
}

fun Cell.previousLeaf(): Cell? {
    val sibling = previousSibling() ?: return parent?.previousLeaf()
    return sibling.lastLeaf()
}

fun Cell.nextLeaf(): Cell? {
    val sibling = nextSibling() ?: return parent?.nextLeaf()
    return sibling.firstLeaf()
}

fun Cell.firstLeaf(): Cell {
    val children = this.getChildren()
    return if (children.isEmpty()) this else children.first().firstLeaf()
}

fun Cell.lastLeaf(): Cell {
    val children = this.getChildren()
    return if (children.isEmpty()) this else children.last().lastLeaf()
}

fun Cell.previousSibling(): Cell? {
    val parent = this.parent ?: return null
    val siblings = parent.getChildren()
    val index = siblings.indexOf(this)
    if (index == -1) throw RuntimeException("$this expected to be a child of $parent")
    val siblingIndex = index - 1
    return if (siblingIndex >= 0) siblings[siblingIndex] else null
}

fun Cell.nextSibling(): Cell? {
    val parent = this.parent ?: return null
    val siblings = parent.getChildren()
    val index = siblings.indexOf(this)
    if (index == -1) throw RuntimeException("$this expected to be a child of $parent")
    val siblingIndex = index + 1
    return if (siblingIndex < siblings.size) siblings[siblingIndex] else null
}

fun Cell.descendants(): Sequence<Cell> = getChildren().asSequence().flatMap { it.descendantsAndSelf() }
fun Cell.descendantsAndSelf(): Sequence<Cell> = sequenceOf(this) + descendants()
fun Cell.ancestors(includeSelf: Boolean = false) = generateSequence(if (includeSelf) this else this.parent) { it.parent }

fun Cell.commonAncestor(other: Cell): Cell = (ancestors(true) - other.ancestors(true).toSet()).last().parent!!

fun Cell.isLeaf() = this.getChildren().isEmpty()
fun Cell.isFirstChild() = previousSibling() == null
fun Cell.isLastChild() = nextSibling() == null

fun Cell.leftAlignedHierarchy() = firstLeaf().ancestors(true).takeWhilePrevious { it.isFirstChild() }
fun Cell.rightAlignedHierarchy() = lastLeaf().ancestors(true).takeWhilePrevious { it.isLastChild() }
fun Cell.centerAlignedHierarchy() = leftAlignedHierarchy().toList().intersect(rightAlignedHierarchy().toSet())

/**
 * Takes all the elements that matches the predicate and one more.
 */
fun <T> Sequence<T>.takeWhilePrevious(predicate: (previous: T) -> Boolean): Sequence<T> {
    var previous: T? = null
    var isFirst = true
    return takeWhile { current ->
        val matches = if (isFirst) true else predicate(previous as T)
        previous = current
        isFirst = false
        matches
    }
}