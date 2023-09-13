/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.kotlin.utils

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class ContextValueTests {

    @Test
    fun testIsolation() = runTest {
        // run multiple suspendable and non-suspendable functions in parallel to ensure they always read their own value
        // and not a value from a different coroutine/thread.

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
