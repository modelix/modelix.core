package org.modelix.mps.sync3

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.io.delete
import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute
import kotlin.io.path.writeText

abstract class MPSTestBase : UsefulTestCase() {

    protected lateinit var project: Project

    override fun runInDispatchThread() = false
    override fun setUp() {
        super.setUp()
        TestApplicationManager.getInstance()
        IModelSyncService.continueOnError = false
    }

    @OptIn(ExperimentalPathApi::class)
    fun openTestProject(testDataName: String? = null, projectName: String = "test-project", beforeOpen: (projectDir: Path) -> Unit = {}): Project {
        val projectDirParent = Path.of("build", "test-projects").absolute()
        projectDirParent.toFile().mkdirs()
        val projectDir = Files.createTempDirectory(projectDirParent, "mps-project")
        projectDir.delete(recursively = true)
        projectDir.toFile().mkdirs()
        projectDir.toFile().deleteOnExit()
        val options = OpenProjectTask().withProjectName(projectName)
        val project = if (testDataName != null) {
            val sourceDir = File("testdata/$testDataName")
            sourceDir.copyRecursively(projectDir.toFile(), overwrite = true)
            beforeOpen(projectDir)
            ProjectManagerEx.getInstanceEx().openProject(projectDir, options)!!
        } else {
            projectDir.resolve(".mps").also { it.toFile().mkdirs() }.resolve("modules.xml").writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                  <component name="MPSProject">
                    <projectModules>
                    </projectModules>
                  </component>
                </project>
                """.trimIndent(),
            )
            projectDir.resolve(".mps/.name").writeText(projectName)
            beforeOpen(projectDir)
            ProjectManagerEx.getInstanceEx().openProject(projectDir, options)!!
        }

        disposeOnTearDownInEdt { project.close() }

        ApplicationManager.getApplication().invokeAndWait {
            // empty - openTestProject executed not in EDT, so, invokeAndWait just forces
            // processing of all events that were queued during project opening
        }

        this.project = project

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

    protected suspend fun <R> command(body: () -> R): R {
        var result: R? = null
        withContext(Dispatchers.Main) {
            ApplicationManager.getApplication().invokeAndWait({
                mpsProject.modelAccess.executeCommand { result = body() }
            }, ModalityState.NON_MODAL)
        }
        return result as R
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

fun Project.close() {
    ApplicationManager.getApplication().invokeLaterOnWriteThread {
        runCatching {
            ProjectManager.getInstance().closeAndDispose(this)
        }
    }
    ApplicationManager.getApplication().invokeAndWait { }
}
