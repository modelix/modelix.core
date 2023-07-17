package org.modelix.modelql.core

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class PerformanceTests {

    @Test
    fun flowBasedFilterPerformance() = runTest {
        val query = buildMonoQuery<Int, Int> { it.filter { it.equalTo(0) } }
        val intRange = 1..10000

        compareBenchmark(30, 100.0, {
            query.asFlow(intRange.asFlow().asStepFlow()).count()
        }, {
            intRange.asFlow().filter { it == 0 }.count()
        })
    }

    @Test
    fun sequenceBasedFilterPerformance() = runTest {
        val query = buildMonoQuery<Int, Int> { it.filter { it.equalTo(0) } }
        val intRange = 1..100000

        compareBenchmark(100, 5.0, {
            query.asSequence(intRange.asSequence()).count()
        }, {
            intRange.asSequence().filter { it == 0 }.count()
        })
    }

    @Test
    fun flowVsSequence() = runTest {
        compareBenchmark(100, 2.0, {
            (1..10000)
                .map { it + 10 }
                .flatMap { a -> (1..10).asSequence().map { b -> a * b } }
                .count()
        }, {
            (1..10000).asFlow()
                .map { it + 10 }
                .flatMapConcat { a -> (1..10).asFlow().map { b -> a * b } }
                .count()
        })
    }

    @Test
    fun flowVsSequence2() = runTest {
        compareBenchmark(100, 2.0, {
            (1..1000)
                .map { it + 10 }
                .flatMap { a -> (1..10).asSequence().map { b -> a * b } }
                .filter { a -> (1..10).asSequence().map { b -> a * b }.last() != 0 }
                .count()
        }, {
            (1..1000).asFlow()
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
        println()
        withTimeout(10.seconds) {
            val maxRetries = 3
            for (retry in 1..maxRetries) {
                val time1 = runBenchmark(iterations) {
                    impl1()
                }
                println("($retry) implementation 1: $time1")

                val time2: Duration = runBenchmark(iterations) {
                    impl2()
                }
                println("($retry) implementation 2: $time2")

                val factor = time1 / time2
                val message = "($retry) Implementation 1 is $factor times slower than implementation 2"
                println(message)
                if (factor <= allowedFactor) return@withTimeout
                if (retry != maxRetries && factor <= allowedFactor * 10) continue
                fail(message)
            }
        }
    }
}
