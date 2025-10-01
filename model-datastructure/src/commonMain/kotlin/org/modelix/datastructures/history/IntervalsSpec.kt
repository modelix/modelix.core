package org.modelix.datastructures.history

import kotlinx.datetime.Instant

interface IntervalsSpec {
    /**
     * @return -1 if the time is excluded
     */
    fun getIntervalIndex(time: Instant): Long

    fun includes(time: Instant): Boolean

    fun intersects(range: ClosedRange<Instant>): Boolean
}
