package org.modelix.datastructures.history

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class EquidistantIntervalsSpec(duration: Duration) : IntervalsSpec {
    val duration: Duration = duration.coerceAtLeast(1.seconds)

    override fun getIntervalIndex(time: Instant): Long {
        return time.epochSeconds / duration.inWholeSeconds
    }

    override fun includes(time: Instant): Boolean = true

    override fun intersects(range: ClosedRange<Instant>): Boolean = true
}
