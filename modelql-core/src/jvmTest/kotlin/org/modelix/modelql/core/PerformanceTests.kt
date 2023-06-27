package org.modelix.modelql.core

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class PerformanceTests {

    @Test
    fun flowBasedFilterPerformance() = runTest {
        val query = buildMonoQuery<Int, Int> { it.filter { false.asMono() } }
        val intRange = 1..100000

        val iterations = 1000
        val timeWithFlow = runBenchmark(iterations) {
            intRange.asFlow().filter { false }.count()
        }
        println("timeWithFlow: $timeWithFlow")

        val timeWithQuery: Duration = runBenchmark(iterations) {
            query.asFlow(intRange.asFlow()).count()
        }
        println("timeWithQuery: $timeWithQuery")

        val factor = timeWithQuery / timeWithFlow
        val message = "A query is $factor times slower"
        assertTrue(factor < 5, message)
        println(message)
    }

    @Test
    fun sequenceBasedFilterPerformance() = runTest {
        val query = buildMonoQuery<Int, Int> { it.filter { false.asMono() } }
        val intRange = 1..100000

        val iterations = 1000
        val timeWithSequence = runBenchmark(iterations) {
            intRange.asSequence().filter { false }.count()
        }
        println("timeWithSequence: $timeWithSequence")

        val timeWithQuery = runBenchmark(iterations) {
            query.asSequence(intRange.asSequence()).count()
        }
        println("timeWithQuery: $timeWithQuery")

        val factor = timeWithQuery / timeWithSequence
        val message = "A query is $factor times slower"
        assertTrue(factor < 5, message)
        println(message)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun runBenchmark(iterations: Int = 10, body: suspend () -> Unit): Duration {
        var minTime: Duration? = null
        for (i in 1..iterations) {
            val time = measureTime { body() }
            minTime = if (minTime == null) time else minOf(minTime, time)
        }
        return minTime!!
    }
}
