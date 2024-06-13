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
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute
import kotlin.io.path.deleteRecursively

/**
 * Based on org.jetbrains.uast.test.env.AbstractLargeProjectTest
 */
@Suppress("removal")
abstract class MpsTestBase(val testDataName: String?) : UsefulTestCase() {
    init {
        // workaround for MPS 2023.3 failing to start in test mode
        System.setProperty("intellij.platform.load.app.info.from.resources", "true")
    }

    protected lateinit var project: Project

    override fun runInDispatchThread() = false

    override fun setUp() {
        super.setUp()
        TestApplicationManager.getInstance()
        project = openTestProject()
    }

    override fun tearDown() {
        super.tearDown()
    }

    @OptIn(ExperimentalPathApi::class)
    private fun openTestProject(): Project {
        val projectDirParent = Path.of("build", "test-projects").absolute()
        projectDirParent.toFile().mkdirs()
        val projectDir = Files.createTempDirectory(projectDirParent, "mps-project")
        projectDir.deleteRecursively()
        projectDir.toFile().mkdirs()
        projectDir.toFile().deleteOnExit()
        val project = if (testDataName != null) {
            val sourceDir = File("testdata/$testDataName")
            sourceDir.copyRecursively(projectDir.toFile(), overwrite = true)
            ProjectManagerEx.getInstanceEx().openProject(projectDir, OpenProjectTask())!!
        } else {
            ProjectManagerEx.getInstanceEx().newProject(projectDir, OpenProjectTask())!!
        }

        disposeOnTearDownInEdt { ProjectManager.getInstance().closeAndDispose(project) }

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

    protected fun <R> runCommandOnEDT(body: () -> R): R {
        var result: R? = null
        val exception = ThreadUtils.runInUIThreadAndWait {
            mpsProject.modelAccess.executeCommand {
                result = body()
            }
        }
        if (exception != null) {
            throw exception
        }
        checkNotNull(result) {
            "The result was null even those no exception was thrown."
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
