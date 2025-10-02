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
)

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
)

fun HistoryInterval.toJS() = HistoryIntervalJS(
    firstVersionHash = firstVersionHash.toString(),
    lastVersionHash = lastVersionHash.toString(),
    size = size.toInt(),
    minTime = minTime.toJSDate(),
    maxTime = maxTime.toJSDate(),
    authors = authors.toTypedArray(),
)
