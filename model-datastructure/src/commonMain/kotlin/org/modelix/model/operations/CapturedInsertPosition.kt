package org.modelix.model.operations

import org.modelix.model.api.INodeReference

class CapturedInsertPosition(val siblingsBefore: List<INodeReference>, val siblingsAfter: List<INodeReference>) {
    constructor(index: Int, children: List<INodeReference>) : this(
        if (index >= 0) children.take(index) else children,
        if (index >= 0) children.drop(index) else emptyList(),
    )

    fun findIndex(children: List<INodeReference>): Int {
        return findIndexRange(children).last
    }

    fun findIndex(children: List<INodeReference>, preferredIndex: Int): Int {
        val range = findIndexRange(children)
        if (range.contains(preferredIndex)) return preferredIndex
        if (preferredIndex <= range.first) return range.first
        return range.last
    }

    fun findIndexRange(children: List<INodeReference>): IntRange {
        if (children == (siblingsBefore + siblingsAfter)) return siblingsBefore.size..siblingsBefore.size

        var leftIndex = 0
        var rightIndex = children.size

        for (sibling in siblingsBefore.reversed()) {
            val index = children.indexOf(sibling)
            if (index != -1) {
                leftIndex = index + 1
                break
            }
        }

        for (sibling in siblingsAfter) {
            val index = children.indexOf(sibling)
            if (index != -1) {
                rightIndex = index
                break
            }
        }

        return if (leftIndex < rightIndex) {
            leftIndex..rightIndex
        } else {
            leftIndex..leftIndex
        }
    }
}
