package org.modelix.model.client2

import kotlinx.datetime.toJSDate
import org.modelix.datastructures.history.HistoryInterval
import kotlin.js.Date

/**
 * Contains a subset of version data like in [CPVersion].
 *
 * The full version data of an [CPVersion] is not exposed because most parts model API are not exposed to JS yet.
 * See https://issues.modelix.org/issue/MODELIX-962
 */
@JsExport
data class VersionInformationJS(
    /**
     * Author of the version.
     */
    val author: String?,
    /**
     * Creation time of the version.
     */
    val time: Date?,

    /**
     * hash string of the version
     */
    val versionHash: String?,

    /**
     * Attributes stored on this version.
     */
    val attributes: Array<AttributeEntryJS> = emptyArray(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VersionInformationJS) return false
        return author == other.author &&
            time == other.time &&
            versionHash == other.versionHash &&
            attributes.asMap() == other.attributes.asMap()
    }

    override fun hashCode(): Int {
        var result = author.hashCode()
        result = 31 * result + time.hashCode()
        result = 31 * result + versionHash.hashCode()
        result = 31 * result + attributes.asMap().hashCode()
        return result
    }
}

/**
 * A single key-value attribute, used for writing attributes to a version.
 */
@JsExport
class AttributeEntryJS(val key: String, val value: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttributeEntryJS) return false
        return key == other.key && value == other.value
    }

    override fun hashCode(): Int = 31 * key.hashCode() + value.hashCode()
}

internal fun Array<AttributeEntryJS>.asMap(): Map<String, String> = associate { it.key to it.value }

internal fun Map<String, String>.toAttributeEntriesJS(): Array<AttributeEntryJS> =
    entries.map { AttributeEntryJS(it.key, it.value) }.toTypedArray()

/**
 * An aggregated attribute entry from a history interval, where multiple versions may have
 * contributed different values for the same key.
 */
@JsExport
class AggregatedAttributeEntryJS(val key: String, val firstValues: Array<String>, val lastValues: Array<String>)

@JsExport
class HistoryIntervalJS(
    val firstVersionHash: String,
    val lastVersionHash: String,
    /**
     * Number of versions contained in this interval.
     */
    val size: Int,
    val minTime: Date,
    val maxTime: Date,
    val authors: Array<String>,
    /**
     * Aggregated attributes from all versions in this interval.
     * Each entry contains all distinct values seen for that key across all versions.
     */
    val attributes: Array<AggregatedAttributeEntryJS>,
)

fun HistoryInterval.toJS() = HistoryIntervalJS(
    firstVersionHash = firstVersionHash.toString(),
    lastVersionHash = lastVersionHash.toString(),
    size = size.toInt(),
    minTime = minTime.toJSDate(),
    maxTime = maxTime.toJSDate(),
    authors = authors.toTypedArray(),
    attributes = attributes.getEntries().map {
        AggregatedAttributeEntryJS(it.key, it.value.first.toTypedArray(), it.value.last.toTypedArray())
    }.toTypedArray(),
)
