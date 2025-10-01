package org.modelix.datastructures.history

import kotlinx.datetime.Instant
import kotlin.time.Duration

class EquidistantIntervalsSpec(val duration: Duration) : IntervalsSpec {
    override fun getIntervalIndex(time: Instant): Long {
        return time.epochSeconds / duration.inWholeSeconds
    }

    override fun includes(time: Instant): Boolean = true

    override fun intersects(range: ClosedRange<Instant>): Boolean = true
}
