package org.modelix.editor

abstract class TreeList<E> : Iterable<E> {
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

private class TreeListParent<E>(children_: Iterable<TreeList<E>>) : TreeList<E>() {
    val children: List<TreeList<E>> = children_.filter { it !is TreeListEmpty }
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
        return when (this.children.size) {
            0 -> TreeListEmpty()
            1 -> this.children.first()
            else -> this
        }
    }
}

private class TreeListEmpty<E> : TreeList<E>() {
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