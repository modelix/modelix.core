package org.modelix.datastructures.history

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

interface IHistoryQueries {

    /**
     * Splits the history between versions where the time difference is greater or equal to [delay].
     *
     * @param headVersion starting point for history computations.
     * @param timeRange return versions in this time range only
     * @param delay time between two changes after which it is considered to be a new session
     */
    suspend fun sessions(
        timeRange: ClosedRange<Instant>? = null,
        delay: Duration = 5.minutes,
        pagination: PaginationParameters = PaginationParameters.DEFAULT,
    ): List<HistoryInterval>

    /**
     *
     * @param headVersion starting point for history computations.
     * @param timeRange return versions in this time range only
     * @param interval splits the timeline into equally sized intervals and returns a summary of the contained versions
     */
    suspend fun intervals(
        timeRange: ClosedRange<Instant>? = null,
        interval: Duration,
        pagination: PaginationParameters = PaginationParameters.DEFAULT,
    ): List<HistoryInterval>

    /**
     * A paginated view on the list of all versions in the history sorted by their timestamp. Latest version first.
     * @param headVersion starting point for history computations. For a paginated view this value should be the same
     *        and the value for [skip] should be incremented instead. Only then it's guaranteed that the returned list
     *        is complete.
     */
    suspend fun range(
        timeRange: ClosedRange<Instant>? = null,
        pagination: PaginationParameters = PaginationParameters.DEFAULT,
    ): List<HistoryEntry>

    /**
     * Split the history at the specified [splitPoints]. The split point itself is part of the interval that ends at
     * that point.
     */
    suspend fun splitAt(splitPoints: List<Instant>): List<HistoryInterval>
}
