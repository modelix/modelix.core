package org.modelix.datastructures.history

import kotlinx.serialization.Serializable

@Serializable
data class AttributesAggregation(val attributes: Map<String, AttributeValuesAggregation>) {
    fun getEntries() = attributes.entries

    operator fun get(key: String) = getValues(key)

    fun getValues(key: String): AttributeValuesAggregation {
        return attributes[key] ?: AttributeValuesAggregation.EMPTY
    }

    fun merge(other: AttributesAggregation): AttributesAggregation {
        return AttributesAggregation(
            (attributes.keys + other.attributes.keys).associateWith { getValues(it).merge(other.getValues(it)) },
        )
    }

    fun isEmpty() = attributes.isEmpty()

    operator fun plus(other: AttributesAggregation) = merge(other)

    override fun toString(): String {
        return attributes.toString()
    }

    companion object {
        val EMPTY = AttributesAggregation(emptyMap())

        fun of(entries: Map<String, String>): AttributesAggregation {
            return AttributesAggregation(entries.mapValues { AttributeValuesAggregation.of(it.value) })
        }
    }
}

@Serializable
data class AttributeValuesAggregation(val first: Set<String>, val last: Set<String>) {
    init {
        require(last.intersect(first).isEmpty()) {
            "Duplicate values: $this"
        }
    }

    fun merge(other: AttributeValuesAggregation): AttributeValuesAggregation {
        return of(first + other.first + last + other.last)
    }

    override fun toString(): String {
        return if (first.size + last.size <= MAX_VALUES) {
            (first.sorted() + last.sorted()).joinToString(", ")
        } else {
            first.sorted().joinToString(", ") + ", ..., " + last.sorted().joinToString(", ")
        }
    }

    companion object {
        val MAX_VALUES = 20
        val MAX_VALUES_START = MAX_VALUES / 2
        val MAX_VALUES_END = MAX_VALUES / 2
        val EMPTY = AttributeValuesAggregation(emptySet(), emptySet())

        fun of(values: Set<String>): AttributeValuesAggregation {
            val sorted = values.sorted()
            val size2 = (sorted.size / 2).coerceAtMost(MAX_VALUES_END)
            val size1 = (sorted.size - size2).coerceAtMost(MAX_VALUES_START)
            return AttributeValuesAggregation(
                first = sorted.take(size1).toSet(),
                last = sorted.takeLast(size2).toSet(),
            )
        }

        fun of(vararg value: String): AttributeValuesAggregation {
            return of(value.toSet())
        }

        fun of(value: String): AttributeValuesAggregation {
            return AttributeValuesAggregation(setOf(value), emptySet())
        }
    }
}
