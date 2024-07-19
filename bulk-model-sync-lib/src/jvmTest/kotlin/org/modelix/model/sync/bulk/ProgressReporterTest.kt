package org.modelix.model.sync.bulk

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KLoggingEventBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
import io.github.oshai.kotlinlogging.Marker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressReporterTest {

    @Test
    fun `prints with carriage return if on TTY`() {
        val logger = KotlinLogging.logger { }
        var printed: String? = null
        val reporter = ProgressReporter(1UL, logger, print = { arg -> printed = arg }, isTty = { true })

        reporter.step(1UL)

        assertTrue(printed!!.startsWith("\r(1 / 1)"), "Expected '$printed' to start with carriage return and count")
    }

    @Test
    fun `prints to logger if not on TTY`() {
        val logger = TestLogger()
        val reporter = ProgressReporter(1UL, logger, print = { }, isTty = { false })

        reporter.step(1UL)

        assertTrue((logger.infoMessages[0] as String).startsWith("(1 / 1)"))
    }

    @Test
    fun `avoids spamming the logger for every increment`() {
        val logger = TestLogger()
        val total = 400UL
        val reporter = ProgressReporter(total, logger, print = { }, isTty = { false })

        (1UL..total).forEach { step ->
            reporter.step(step)
        }

        // Includes printing the first element, which is always shown
        assertEquals(101, logger.infoMessages.size)
    }

    class TestLogger : KLogger {

        var infoMessages: MutableList<Any?> = mutableListOf()

        override fun at(level: Level, marker: Marker?, block: KLoggingEventBuilder.() -> Unit) {
            // not needed in this test
        }

        override val name: String
            get() = "" // not needed in this test

        override fun isLoggingEnabledFor(level: Level, marker: Marker?): Boolean {
            // not needed in this test
            return true
        }

        override fun <T : Throwable> catching(throwable: T) {
            // not needed in this test
        }

        override fun debug(msg: () -> Any?) {
            // not needed in this test
        }

        override fun debug(t: Throwable?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun debug(marker: Marker?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun debug(marker: Marker?, t: Throwable?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun debug(msg: String?) {
            // not needed in this test
        }

        override fun debug(format: String?, arg: Any?) {
            // not needed in this test
        }

        override fun debug(format: String?, arg1: Any?, arg2: Any?) {
            // not needed in this test
        }

        override fun debug(format: String?, vararg arguments: Any?) {
            // not needed in this test
        }

        override fun debug(msg: String?, t: Throwable?) {
            // not needed in this test
        }

        override fun entry(vararg argArray: Any?) {
            // not needed in this test
        }

        override fun error(msg: () -> Any?) {
            // not needed in this test
        }

        override fun error(t: Throwable?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun error(marker: Marker?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun error(marker: Marker?, t: Throwable?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun error(msg: String?) {
            // not needed in this test
        }

        override fun error(format: String?, arg: Any?) {
            // not needed in this test
        }

        override fun error(format: String?, arg1: Any?, arg2: Any?) {
            // not needed in this test
        }

        override fun error(format: String?, vararg arguments: Any?) {
            // not needed in this test
        }

        override fun error(msg: String?, t: Throwable?) {
            // not needed in this test
        }

        override fun exit() {
            // not needed in this test
        }

        override fun <T> exit(result: T): T {
            // not needed in this test
            return result
        }

        override fun info(msg: () -> Any?) {
            infoMessages.add(msg())
        }

        override fun info(t: Throwable?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun info(marker: Marker?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun info(marker: Marker?, t: Throwable?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun info(msg: String?) {
            // not needed in this test
        }

        override fun info(format: String?, arg: Any?) {
            // not needed in this test
        }

        override fun info(format: String?, arg1: Any?, arg2: Any?) {
            // not needed in this test
        }

        override fun info(format: String?, vararg arguments: Any?) {
            // not needed in this test
        }

        override fun info(msg: String?, t: Throwable?) {
            // not needed in this test
        }

        override fun <T : Throwable> throwing(throwable: T): T {
            // not needed in this test
            return throwable
        }

        override fun trace(msg: () -> Any?) {
            // not needed in this test
        }

        override fun trace(t: Throwable?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun trace(marker: Marker?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun trace(marker: Marker?, t: Throwable?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun trace(msg: String?) {
            // not needed in this test
        }

        override fun trace(format: String?, arg: Any?) {
            // not needed in this test
        }

        override fun trace(format: String?, arg1: Any?, arg2: Any?) {
            // not needed in this test
        }

        override fun trace(format: String?, vararg arguments: Any?) {
            // not needed in this test
        }

        override fun trace(msg: String?, t: Throwable?) {
            // not needed in this test
        }

        override fun warn(msg: () -> Any?) {
            // not needed in this test
        }

        override fun warn(t: Throwable?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun warn(marker: Marker?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun warn(marker: Marker?, t: Throwable?, msg: () -> Any?) {
            // not needed in this test
        }

        override fun warn(msg: String?) {
            // not needed in this test
        }

        override fun warn(format: String?, arg: Any?) {
            // not needed in this test
        }

        override fun warn(format: String?, vararg arguments: Any?) {
            // not needed in this test
        }

        override fun warn(format: String?, arg1: Any?, arg2: Any?) {
            // not needed in this test
        }

        override fun warn(msg: String?, t: Throwable?) {
            // not needed in this test
        }
    }
}
