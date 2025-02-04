package org.modelix.model.sync.bulk.lib.test

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.io.delete
import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.MPSModuleRepository
import org.jetbrains.mps.openapi.model.EditableSModel
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.data.ModelData
import org.modelix.model.data.asData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.mpsadapters.asReadableNode
import org.modelix.model.mpsadapters.asWritableNode
import org.modelix.model.persistent.MapBaseStore
import org.modelix.model.sync.bulk.ModelSynchronizer
import org.modelix.model.sync.bulk.NodeAssociationFromModelServer
import org.modelix.model.sync.bulk.NodeAssociationToModelServer
import org.modelix.mps.api.ModelixMpsApi
import org.modelix.mps.model.sync.bulk.MPSProjectSyncFilter
import org.w3c.dom.Element
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolute

class RecreateProjectFromModelServerTest : UsefulTestCase() {

    protected lateinit var project: Project

    override fun runInDispatchThread() = false

    override fun setUp() {
        super.setUp()
    }

    fun `test same file content`() {
        TestApplicationManager.getInstance()

        val originalProject = openTestProject("nonTrivialProject")
        project = originalProject

        val store = ObjectStoreCache(MapBaseStore()).getAsyncStore()
        val idGenerator = IdGenerator.getInstance(0xabcd)
        val emptyVersion = CLVersion.createRegularVersion(
            id = idGenerator.generate(),
            time = null,
            author = this::class.java.name,
            tree = CLTree.builder(store).repositoryId("unit-test-repo").build(),
            baseVersion = null,
            operations = emptyArray(),
        )
        val branch = PBranch(emptyVersion.getTree(), idGenerator)
        mpsProject.modelAccess.runReadAction {
            branch.runWrite {
                val mpsRoot = mpsProject.repository.asReadableNode()
                val modelServerRoot = branch.getRootNode().asWritableNode()
                ModelSynchronizer(
                    filter = MPSProjectSyncFilter(listOf(mpsProject), toMPS = false),
                    sourceRoot = mpsRoot,
                    targetRoot = modelServerRoot,
                    nodeAssociation = NodeAssociationToModelServer(branch),
                ).synchronize()
                println(ModelData(root = modelServerRoot.asLegacyNode().asData()).toJson())
            }
        }

        fun filterFiles(files: Map<String, String>) = files.filter {
            val name = it.key
            if (name.startsWith(".mps/")) {
                false // name == ".mps/modules.xml"
            } else if (name.contains("/source_gen") || name.contains("/classes_gen")) {
                false
            } else {
                true
            }
        }

        val originalContents = filterFiles(originalProject.captureFileContents())
        originalProject.close()

        val emptyProject = openTestProject(null)
        project = emptyProject

        mpsProject.modelAccess.executeCommandInEDT {
            branch.runRead {
                val mpsRoot = mpsProject.repository.asWritableNode()
                val modelServerRoot = branch.getRootNode().asReadableNode()
                val modelSynchronizer = ModelSynchronizer(
                    filter = MPSProjectSyncFilter(listOf(mpsProject), toMPS = true),
                    sourceRoot = modelServerRoot,
                    targetRoot = mpsRoot,
                    nodeAssociation = NodeAssociationFromModelServer(branch, mpsRoot.getModel()),
                )
                ModelixMpsApi.runWithProject(mpsProject) {
                    modelSynchronizer.synchronize()
                }
            }
        }

        val syncedContents = filterFiles(emptyProject.captureFileContents())

        fun Map<String, String>.contentsAsString(): String {
            return entries.sortedBy { it.key }.joinToString("\n\n\n") { "------ ${it.key} ------\n${it.value}" }
                .replace("""<concept id="8281020627045179518" name=""""", """<concept id="8281020627045179518" name="NewLanguage.structure.MyChild"""")
                .replace("""<property id="8281020627045236732" name=""""", """<property id="8281020627045236732" name="value"""")
                .replace("""<child id="8281020627045179519" name=""""", """<child id="8281020627045179519" name="children"""")
                .replace("""<concept id="8281020627045179517" name=""""", """<concept id="8281020627045179517" name="NewLanguage.structure.MyRoot"""")
        }

        assertEquals(
            originalContents.contentsAsString(),
            syncedContents.contentsAsString(),
        )
    }

    override fun tearDown() {
        super.tearDown()
    }

    @OptIn(ExperimentalPathApi::class)
    private fun openTestProject(testDataName: String?): Project {
        val projectDirParent = Path.of("build", "test-projects").absolute()
        projectDirParent.toFile().mkdirs()
        val projectDir = Files.createTempDirectory(projectDirParent, "mps-project")
        projectDir.delete(recursively = true)
        projectDir.toFile().mkdirs()
        projectDir.toFile().deleteOnExit()
        val options = OpenProjectTask().withProjectName("test-project")
        val project = if (testDataName != null) {
            val sourceDir = File("testdata/$testDataName")
            sourceDir.copyRecursively(projectDir.toFile(), overwrite = true)
            ProjectManagerEx.getInstanceEx().openProject(projectDir, options)!!
        } else {
            ProjectManagerEx.getInstanceEx().newProject(projectDir, options)!!
        }

        disposeOnTearDownInEdt { project.close() }

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

fun Project.close() {
    ApplicationManager.getApplication().invokeLaterOnWriteThread {
        runCatching {
            ProjectManager.getInstance().closeAndDispose(this)
        }
    }
    ApplicationManager.getApplication().invokeAndWait { }
}

private fun Project.captureFileContents(): Map<String, String> {
    ApplicationManager.getApplication().invokeAndWait {
        MPSModuleRepository.getInstance().modelAccess.runWriteAction {
            for (module in ProjectHelper.fromIdeaProject(this)!!.projectModules.flatMap {
                listOf(it) + ((it as? Language)?.generators ?: emptyList())
            }) {
                module as AbstractModule
                module.save()
                for (model in module.models.filterIsInstance<EditableSModel>()) {
                    ModelixMpsApi.forceSave(model)
                }
            }
        }
        ApplicationManager.getApplication().saveAll()
        save()
    }
    return File(this.basePath).walk().filter { it.isFile }.associate { file ->
        val name = file.absoluteFile.relativeTo(File(basePath).absoluteFile).path
        val content = file.readText().trim()
        val xmlEndings = setOf("mps", "devkit", "mpl", "msd")
        val normalizedContent = when {
            xmlEndings.contains(name.substringAfterLast(".")) -> normalizeXmlFile(content)
            else -> content
        }
        name to normalizedContent
    }
}

private fun normalizeXmlFile(content: String): String {
    val xml = readXmlFile(content.byteInputStream())
    xml.visitAll { node ->
        if (node !is Element) return@visitAll
        when (node.tagName) {
            "node" -> {
                node.childElements("property").sortByRole()
                node.childElements("ref").sortByRole()
                node.childElements("node").sortByRole()
            }
            "sourceRoot" -> {
                val location = node.getAttribute("location")
                val path = node.getAttribute("path")
                if (path.isNullOrEmpty() && !location.isNullOrEmpty()) {
                    val contentPath = (node.parentNode as Element).getAttribute("contentPath")
                    node.removeAttribute("location")
                    node.setAttribute("path", "$contentPath/$location")
                }
            }
        }
    }
    return xmlToString(xml).lineSequence().map { it.trim() }.filter { it.isEmpty() }.joinToString("\n")
}

private fun List<Element>.sortByRole() {
    if (size < 2) return
    val sorted = sortedBy { it.getAttribute("role") }
    for (i in (0..sorted.lastIndex - 1).reversed()) {
        sorted[i].parentNode.insertBefore(sorted[i], sorted[i + 1])
    }
}
