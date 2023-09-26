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

package org.modelix.mps.sync.util

import com.intellij.openapi.application.ApplicationManager
import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.project.IProject
import jetbrains.mps.project.ProjectManager
import jetbrains.mps.smodel.MPSModuleRepository
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.mps.openapi.module.SRepository
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.Timer

// status: ready to test
object CommandHelper {

    private val logger = mu.KotlinLogging.logger {}

    private val queue = mutableListOf<Pair<Runnable, Boolean>>()

    private val timerActionEvent = object : ActionListener {
        override fun actionPerformed(e: ActionEvent?) {
            if (project == null) {
                return
            }
            timer.stop()
            val queueCopy = queue.toImmutableList()
            queue.clear()
            queueCopy.forEach { entry ->
                try {
                    executeCommand(project!!.repository, entry.second, entry.first)
                } catch (ex: Exception) {
                    logger.error(ex) { ex.message }
                }
            }
        }
    }

    private val timer: Timer = Timer(10, timerActionEvent)

    private val project: IProject?
        get() = ProjectManager.getInstance().openedProjects.firstOrNull()

    fun runInCommand(runnable: Runnable) = runInCommand(runnable, false)

    fun runInUndoTransparentCommand(runnable: Runnable) = runInCommand(runnable, true)

    private fun runInCommand(runnable: Runnable, undoTransparent: Boolean) {
        if (project == null) {
            queue.add(Pair(runnable, undoTransparent))
            if (!timer.isRunning) {
                timer.start()
            }
        } else {
            var ex: Throwable? = null
            val runnableWithExceptionHandling = {
                try {
                    runnable.run()
                } catch (t: Throwable) {
                    ex = t
                }
            }
            if (ThreadUtils.isInEDT()) {
                executeCommand(project!!.repository, undoTransparent, runnableWithExceptionHandling)
            } else {
                ApplicationManager.getApplication().invokeAndWait {
                    executeCommand(project!!.repository, undoTransparent, runnableWithExceptionHandling)
                }
            }
            if (ex != null) {
                throw RuntimeException("Exception in command", ex)
            }
        }
    }

    private fun executeCommand(repository: SRepository, undoTransparent: Boolean, runnable: Runnable) {
        ThreadUtils.assertEDT()
        val modelAccess = repository.modelAccess
        if (modelAccess.canWrite()) {
            runnable.run()
        } else {
            if (undoTransparent) {
                modelAccess.executeUndoTransparentCommand(runnable)
            } else {
                modelAccess.executeCommand(runnable)
            }
        }
    }

    fun getSRepository(): SRepository {
        val openedProjects = ProjectManager.getInstance().openedProjects
        val projectRepo = openedProjects.map { it.repository }.firstOrNull()
        return projectRepo ?: MPSModuleRepository.getInstance()
    }
}
