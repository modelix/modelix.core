package org.modelix.datastructures.history

import kotlinx.datetime.Instant
import org.modelix.datastructures.objects.Object
import org.modelix.model.lazy.CLVersion
import org.modelix.streams.getSuspending
import org.modelix.streams.iterateSuspending
import kotlin.collections.plus
import kotlin.collections.plusAssign
import kotlin.time.Duration

class HistoryQueries(val historyIndex: suspend () -> Object<HistoryIndexNode>) : IHistoryQueries {

    /**
     * Splits the history into intervals where the time difference between two versions is less or equal to [delay].
     */
    override suspend fun sessions(
        timeRange: ClosedRange<Instant>?,
        delay: Duration,
    ): List<HistoryInterval> {
        val index: Object<HistoryIndexNode> = historyIndex()
        val sessions = ArrayList<HistoryInterval>()
        var previousMinTime = Instant.Companion.fromEpochSeconds(Long.MAX_VALUE)

        // In the worst case two adjacent intervals contain a single entry directly at the border.
        // The maximum difference between these two entries is less than two times the interval.
        val interval = delay / 2

        index.data.splitAtInterval(EquidistantIntervalsSpec(interval).withTimeRangeFilter(timeRange)).iterateSuspending(index.graph) {
            if (previousMinTime - it.maxTime >= delay) {
                sessions += HistoryInterval(
                    firstVersionHash = it.firstVersion.getHash(),
                    lastVersionHash = it.lastVersion.getHash(),
                    size = it.size,
                    minTime = it.minTime,
                    maxTime = it.maxTime,
                    authors = it.authors,
                )
            } else {
                val entry = sessions[sessions.lastIndex]
                sessions[sessions.lastIndex] = HistoryInterval(
                    firstVersionHash = it.firstVersion.getHash(),
                    lastVersionHash = entry.lastVersionHash,
                    size = entry.size + it.size,
                    minTime = minOf(entry.minTime, it.minTime),
                    maxTime = maxOf(entry.maxTime, it.maxTime),
                    authors = entry.authors + it.authors,
                )
            }
            previousMinTime = it.minTime
        }

        return sessions
    }

    override suspend fun intervals(
        timeRange: ClosedRange<Instant>?,
        interval: Duration,
    ): List<HistoryInterval> {
        return intervals(EquidistantIntervalsSpec(interval).withTimeRangeFilter(timeRange))
    }

    suspend fun intervals(
        intervalsSpec: IntervalsSpec,
    ): List<HistoryInterval> {
        val index: Object<HistoryIndexNode> = historyIndex()
        val mergedEntries = ArrayList<HistoryInterval>()
        var previousIntervalId: Long = Long.MAX_VALUE

        index.data.splitAtInterval(intervalsSpec).iterateSuspending(index.graph) {
            val intervalId = intervalsSpec.getIntervalIndex(it.maxTime)
            check(intervalId <= previousIntervalId)
            if (intervalId == previousIntervalId) {
                val entry = mergedEntries[mergedEntries.lastIndex]
                mergedEntries[mergedEntries.lastIndex] = HistoryInterval(
                    firstVersionHash = it.firstVersion.getHash(),
                    lastVersionHash = entry.lastVersionHash,
                    size = entry.size + it.size,
                    minTime = minOf(entry.minTime, it.minTime),
                    maxTime = maxOf(entry.maxTime, it.maxTime),
                    authors = entry.authors + it.authors,
                )
            } else {
                previousIntervalId = intervalId
                mergedEntries += HistoryInterval(
                    firstVersionHash = it.firstVersion.getHash(),
                    lastVersionHash = it.lastVersion.getHash(),
                    size = it.size,
                    minTime = it.minTime,
                    maxTime = it.maxTime,
                    authors = it.authors,
                )
            }
        }

        return mergedEntries
    }

    override suspend fun range(
        timeRange: ClosedRange<Instant>?,
        skip: Long,
        limit: Long,
    ): List<HistoryEntry> {
        val index: Object<HistoryIndexNode> = historyIndex()
        val inTimeRange = if (timeRange == null) index else index.getRange(timeRange).orNull().getSuspending(index.graph)
        if (inTimeRange == null) return emptyList()
        return inTimeRange.data.getRange(skip until (limit + skip))
            .flatMapOrdered { it.getAllVersionsReversed() }
            .flatMapOrdered { it.resolve() }
            .map {
                val version = CLVersion(it)
                HistoryEntry(
                    versionHash = it.getHash(),
                    time = version.getTimestamp() ?: Instant.fromEpochSeconds(0L),
                    author = version.getAuthor(),
                )
            }
            .toList()
            .getSuspending(index.graph)
    }

    override suspend fun splitAt(splitPoints: List<Instant>): List<HistoryInterval> {
        return intervals(SplitPointsIntervalSpec(splitPoints))
    }
}
