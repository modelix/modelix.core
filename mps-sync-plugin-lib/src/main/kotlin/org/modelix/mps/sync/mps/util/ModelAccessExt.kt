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

package org.modelix.mps.sync.mps.util

import org.jetbrains.mps.openapi.module.ModelAccess
import org.modelix.kotlin.utils.UnstableModelixFeature
import java.util.concurrent.CountDownLatch

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun ModelAccess.runWriteInEDTBlocking(callback: Runnable) = runBlocking(this::runWriteInEDT, callback)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun ModelAccess.runReadBlocking(callback: Runnable) = runBlocking(this::runReadAction, callback)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun ModelAccess.runWriteActionCommandBlocking(callback: Runnable) =
    runBlocking(this::runWriteAction) { latch ->
        this.executeCommand {
            callback.run()
            latch.countDown()
        }
    }

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun ModelAccess.runWriteActionInEDTBlocking(callback: Runnable) =
    runBlocking(this::runWriteAction) { latch ->
        this.executeCommandInEDT {
            callback.run()
            latch.countDown()
        }
    }

private fun runBlocking(
    mpsAction: (Runnable) -> Unit,
    callback: Runnable? = null,
    callbackWithLatch: ((CountDownLatch) -> Unit)? = null,
) {
    val latch = CountDownLatch(1)
    try {
        mpsAction {
            callback?.let {
                it.run()
                latch.countDown()
            }
            callbackWithLatch?.invoke(latch)
        }
    } catch (t: Throwable) {
        latch.countDown()
        throw t
    }
    latch.await()
}
