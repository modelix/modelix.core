package org.modelix.kotlin.utils

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class ContextValueTests {

    /*
     * Runs multiple suspendable and non-suspendable functions in parallel to ensure they always read their own value
     * and not a value from a different coroutine/thread.

     * This test starts two coroutines and tries to run into a race-condition. A successful test doesn't proof the
     * correctness, but a failing test proofs its incorrectness. If it ever becomes unstable, meaning it first fails
     * and then succeeds after a second run, this shouldn't be ignored.
     */
    @Test
    fun testIsolation() = runTest {
        val contextValue = ContextValue<String>("a")
        assertEquals("a", contextValue.getValueOrNull())
        coroutineScope {
            launch {
                for (i in 1..10) {
                    contextValue.runInCoroutine("b1") {
                        contextValue.computeWith("b11") {
                            assertEquals("b11", contextValue.getValueOrNull())
                        }
                        assertEquals("b1", contextValue.getValueOrNull())
                        contextValue.computeWith("b12") {
                            assertEquals("b12", contextValue.getValueOrNull())
                        }
                        delay(1.milliseconds)
                    }
                }
            }
            launch {
                for (i in 1..10) {
                    contextValue.runInCoroutine("b2") {
                        assertEquals("b2", contextValue.getValueOrNull())
                        delay(1.milliseconds)
                    }
                }
            }
            for (i in 1..5) {
                contextValue.runInCoroutine("c") {
                    assertEquals("c", contextValue.getValueOrNull())
                    delay(1.milliseconds)
                }
            }
        }
        contextValue.computeWith("d") {
            assertEquals("d", contextValue.getValueOrNull())
        }
        assertEquals("a", contextValue.getValueOrNull())
    }
}
