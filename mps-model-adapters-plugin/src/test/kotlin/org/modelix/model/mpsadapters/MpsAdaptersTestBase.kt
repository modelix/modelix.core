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
package org.modelix.model.mpsadapters

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.UsefulTestCase
import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.system.measureTimeMillis

/**
 * Based on org.jetbrains.uast.test.env.AbstractLargeProjectTest
 */
@Suppress("removal")
abstract class MpsAdaptersTestBase : UsefulTestCase() {

    protected lateinit var project: Project

    override fun runInDispatchThread() = false

    override fun setUp() {
        super.setUp()
        TestApplicationManager.getInstance()
        val projectOpenTime = measureTimeMillis {
            project = openTestProject()
        }
        LOG.warn("Project has been opened successfully in $projectOpenTime ms")
    }

    override fun tearDown() {
        super.tearDown()
    }

    private fun openTestProject(): Project {
        val projectDirParent = Path.of("build", "test-projects").absolute()
        projectDirParent.toFile().mkdirs()
        val projectDir = Files.createTempDirectory(projectDirParent, "mps-project")
        projectDir.toFile().deleteOnExit()
        val project = ProjectManagerEx.getInstanceEx().newProject(projectDir, OpenProjectTask())!!
        disposeOnTearDownInEdt(Runnable { ProjectManager.getInstance().closeAndDispose(project) })

        ApplicationManager.getApplication().invokeAndWait {
            // empty - openTestProject executed not in EDT, so, invokeAndWait just forces
            // processing of all events that were queued during project opening
        }

        return project
    }

    private fun disposeOnTearDownInEdt(runnable: Runnable) {
        Disposer.register(
            testRootDisposable,
            Disposable {
                ApplicationManager.getApplication().invokeAndWait(runnable)
            },
        )
    }

    protected val mpsProject: MPSProject get() {
        return checkNotNull(ProjectHelper.fromIdeaProject(project)) { "MPS project not loaded" }
    }

    protected fun <R> writeAction(body: () -> R): R {
        return mpsProject.modelAccess.computeWriteAction(body)
    }

    protected fun <R> writeActionOnEdt(body: () -> R): R {
        return onEdt { writeAction { body() } }
    }

    protected fun <R> onEdt(body: () -> R): R {
        var result: R? = null
        ThreadUtils.runInUIThreadAndWait {
            result = body()
        }
        return result as R
    }

    protected fun <R> readAction(body: () -> R): R {
        var result: R? = null
        mpsProject.modelAccess.runReadAction {
            result = body()
        }
        return result as R
    }
}
