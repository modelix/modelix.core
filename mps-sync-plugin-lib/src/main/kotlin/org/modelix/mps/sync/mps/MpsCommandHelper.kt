/*
 * Copyright (c) 2024.
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

package org.modelix.mps.sync.mps

import com.intellij.openapi.application.ApplicationManager
import jetbrains.mps.ide.ThreadUtils
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object MpsCommandHelper {

    fun runInUndoTransparentCommand(runnable: Runnable) {
        var propagatedException: Throwable? = null
        val runnableWithPropagatedException = {
            try {
                runnable.run()
            } catch (t: Throwable) {
                propagatedException = t
            }
        }
        val repository = ActiveMpsProjectInjector.activeMpsProject!!.repository
        if (ThreadUtils.isInEDT()) {
            executeCommand(repository, runnableWithPropagatedException)
        } else {
            ApplicationManager.getApplication().invokeAndWait {
                executeCommand(
                    repository,
                    runnableWithPropagatedException,
                )
            }
        }
        propagatedException?.let { throw RuntimeException("Exception in command", it) }
    }

    private fun executeCommand(repository: SRepository, runnable: Runnable) {
        ThreadUtils.assertEDT()
        val modelAccess = repository.modelAccess
        if (modelAccess.canWrite()) {
            runnable.run()
        } else {
            modelAccess.executeUndoTransparentCommand(runnable)
        }
    }
}
