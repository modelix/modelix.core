package org.modelix.datastructures.history

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.modelix.datastructures.objects.ObjectHash

/**
 * A summary of a range of versions.
 */
@Serializable
data class HistoryInterval(
    val firstVersionHash: ObjectHash,
    val lastVersionHash: ObjectHash,
    /**
     * Number of versions contained in this interval.
     */
    val size: Long,
    val minTime: Instant,
    val maxTime: Instant,
    val authors: Set<String>,
    /**
     * Aggregated attributes from all versions in this interval.
     * Maps each attribute key to the set of all distinct values seen across versions.
     */
    val attributes: Map<String, Set<String>> = emptyMap(),
)
