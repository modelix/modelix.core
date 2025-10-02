package org.modelix.datastructures.history

class PaginationParameters(val skip: Int, val limit: Int) {
    companion object {
        val ALL = PaginationParameters(0, Int.MAX_VALUE)
        val DEFAULT = PaginationParameters(0, 200)
    }

    fun asRange() = skip until (skip + limit)

    fun <T> apply(elements: Sequence<T>): List<T> {
        return elements.drop(skip).take(limit).toList()
    }

    fun <T> apply(elements: List<T>): List<T> {
        return elements.subList(skip.coerceAtMost(elements.size), (skip + limit).coerceAtMost(elements.size))
    }
}
