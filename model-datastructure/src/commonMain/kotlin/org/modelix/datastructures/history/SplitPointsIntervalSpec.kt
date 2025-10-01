package org.modelix.datastructures.history

import kotlinx.datetime.Instant

class SplitPointsIntervalSpec(splitPoints: List<Instant>) : IntervalsSpec {
    private val splitPointsSorted = splitPoints.sorted()
    override fun getIntervalIndex(time: Instant): Long {
        val searchResult = splitPointsSorted.binarySearch(time)
        val index = if (searchResult >= 0) searchResult else (-searchResult) - 1
        return index.toLong()
    }

    override fun includes(time: Instant): Boolean = true

    override fun intersects(range: ClosedRange<Instant>): Boolean = true
}
