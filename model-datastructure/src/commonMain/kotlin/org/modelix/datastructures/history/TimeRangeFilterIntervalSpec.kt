package org.modelix.datastructures.history

import kotlinx.datetime.Instant

class TimeRangeFilterIntervalSpec(val spec: IntervalsSpec, val timeRangeFilter: ClosedRange<Instant>) : IntervalsSpec {
    override fun getIntervalIndex(time: Instant): Long {
        return if (timeRangeFilter.contains(time)) spec.getIntervalIndex(time) else -1L
    }

    override fun includes(time: Instant): Boolean {
        return timeRangeFilter.contains(time) && spec.includes(time)
    }

    override fun intersects(range: ClosedRange<Instant>): Boolean {
        return timeRangeFilter.intersects(range) && spec.intersects(range)
    }
}

fun IntervalsSpec.withTimeRangeFilter(range: ClosedRange<Instant>?): IntervalsSpec {
    return if (range == null) this else TimeRangeFilterIntervalSpec(this, range)
}
