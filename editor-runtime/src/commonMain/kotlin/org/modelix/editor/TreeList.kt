package org.modelix.editor

abstract class TreeList<E> : Iterable<E> {
    abstract val size: Int
    operator fun get(index: Int): E {
        require(index in 0 until size) { "$index not in range 0 until $size" }
        return getUnsafe(index)
    }
    abstract fun getUnsafe(index: Int): E
    abstract fun asSequence(): Sequence<E>
    override fun iterator(): Iterator<E> {
        return asSequence().iterator()
    }

    abstract fun withoutLast(): TreeList<E>
    abstract fun withoutFirst(): TreeList<E>
    abstract fun last(): E?
    abstract fun first(): E?
    fun isNotEmpty() = asSequence().iterator().hasNext()

    companion object {
        fun <T> of(vararg elements: T): TreeList<T> {
            return TreeListParent(elements.map { TreeListLeaf(it) }).normalized()
        }

        fun <T> fromCollection(elements: Collection<T>): TreeList<T> {
            return TreeListParent(elements.map { TreeListLeaf(it) }).normalized()
        }

        fun <T> flatten(elements: Iterable<TreeList<T>>): TreeList<T> {
            return TreeListParent(elements.toList()).normalized()
        }
    }
}

private class TreeListLeaf<E>(val element: E) : TreeList<E>() {
    override val size: Int
        get() = 1

    override fun getUnsafe(index: Int): E {
        require(index == 0)
        return element
    }

    override fun asSequence(): Sequence<E> {
        return sequenceOf(element)
    }

    override fun withoutLast(): TreeList<E> {
        return TreeListEmpty()
    }

    override fun withoutFirst(): TreeList<E> {
        return TreeListEmpty()
    }

    override fun last(): E {
        return element
    }

    override fun first(): E? {
        return element
    }
}

private class TreeListParent<E>(val children: List<TreeList<E>>) : TreeList<E>() {
    override val size: Int = children.sumOf { it.size }

    override fun getUnsafe(index: Int): E {
        var relativeIndex = index
        for (child in children) {
            if (relativeIndex < child.size) return child.getUnsafe(relativeIndex)
            relativeIndex -= child.size
        }
        throw IndexOutOfBoundsException("index: $index, size: $size")
    }

    override fun asSequence(): Sequence<E> {
        return children.asSequence().flatMap { it.asSequence() }
    }

    override fun withoutLast(): TreeList<E> {
        return TreeListParent(children.dropLast(1).plusElement(children.last().withoutLast())).normalized()
    }

    override fun withoutFirst(): TreeList<E> {
        return TreeListParent(listOf(children.first().withoutFirst()) + children.drop(1)).normalized()
    }

    override fun last(): E? {
        return children.last().last()
    }

    override fun first(): E? {
        return children.first().first()
    }

    fun normalized(): TreeList<E> {
        val withoutEmpty = this.children.filter { it !is TreeListEmpty }
        return when (withoutEmpty.size) {
            0 -> TreeListEmpty()
            1 -> withoutEmpty.first()
            else -> if (withoutEmpty.size != children.size) TreeListParent(withoutEmpty) else this
        }
    }
}

private class TreeListEmpty<E> : TreeList<E>() {
    override val size: Int
        get() = 0

    override fun getUnsafe(index: Int): E {
        throw IndexOutOfBoundsException("index = $index, size = 0")
    }

    override fun asSequence(): Sequence<E> {
        return emptySequence<E>()
    }

    override fun withoutLast(): TreeList<E> {
        return this
    }

    override fun withoutFirst(): TreeList<E> {
        return this
    }

    override fun last(): E? {
        return null
    }

    override fun first(): E? {
        return null
    }
}