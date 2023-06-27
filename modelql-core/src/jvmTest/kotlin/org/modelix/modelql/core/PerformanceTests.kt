package org.modelix.modelql.core

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
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

        val iterations = 100
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
        assertTrue(factor < 3, message)
        println(message)
    }

    @Test
    fun sequenceBasedFilterPerformance() = runTest {
        val query = buildMonoQuery<Int, Int> { it.filter { false.asMono() } }
        val intRange = 1..100000

        val iterations = 100
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

    @Test
    fun flowVsSequence() = runTest {
        compareBenchmark(10, 3.0, {
            (1..100000)
                .map { it + 10 }
                .flatMap { a -> (1..10).asSequence().map { b -> a * b } }
                .count()
        }, {
            (1..100000).asFlow()
                .map { it + 10 }
                .flatMapConcat { a -> (1..10).asFlow().map { b -> a * b } }
                .count()
        })
    }

    @Test
    fun flowVsSequence2() = runTest {
        compareBenchmark(10, 3.0, {
            (1..10000)
                .map { it + 10 }
                .flatMap { a -> (1..10).asSequence().map { b -> a * b } }
                .filter { a -> (1..10).asSequence().map { b -> a * b }.last() != 0 }
                .count()
        }, {
            (1..10000).asFlow()
                .map { it + 10 }
                .flatMapConcat { a -> (1..10).asFlow().map { b -> a * b } }
                .filter { a -> (1..10).asFlow().map { b -> a * b }.last() != 0 }
                .count()
        })
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

    private suspend fun compareBenchmark(iterations: Int, allowedFactor: Double, impl1: suspend () -> Unit, impl2: suspend () -> Unit) {
        val time1 = runBenchmark(iterations) {
            impl1()
        }
        println("implementation 1: $time1")

        val time2: Duration = runBenchmark(iterations) {
            impl2()
        }
        println("implementation 2: $time2")

        val factor = time1 / time2
        val message = "Implementation 1 is $factor times slower than implementation 2"
        assertTrue(factor <= allowedFactor, message)
        println(message)
    }
}
