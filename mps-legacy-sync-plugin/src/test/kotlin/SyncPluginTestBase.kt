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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import io.ktor.client.HttpClient
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import jetbrains.mps.core.tool.environment.util.SetLibraryContributor
import jetbrains.mps.ide.MPSCoreComponents
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.library.contributor.LibDescriptor
import jetbrains.mps.project.MPSProject
import jetbrains.mps.smodel.adapter.structure.concept.InvalidConcept
import jetbrains.mps.smodel.language.LanguageRegistry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.authorization.installAuthentication
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.mps.ProjectAsNode
import org.modelix.model.mpsadapters.mps.SModuleAsNode
import org.modelix.model.mpsplugin.SModuleUtils
import org.modelix.model.server.handlers.KeyValueLikeModelServer
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.mps.sync.ModelSyncService
import org.modelix.mps.sync.api.IBinding
import org.modelix.mps.sync.api.ISyncService
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Suppress("removal")
@OptIn(UnstableModelixFeature::class)
abstract class SyncPluginTestBase(private val testDataName: String?) : HeavyPlatformTestCase() {
    protected lateinit var httpClient: HttpClient
    protected lateinit var syncService: ISyncService
    protected lateinit var projectDir: Path
    protected val json = Json { prettyPrint = true }
    protected lateinit var initialDumpFromMPS: NodeData
    protected val defaultBranchRef = RepositoryId("default").getBranchReference()

    protected val mpsProject: MPSProject get() {
        return checkNotNull(ProjectHelper.fromIdeaProject(project)) { "MPS project not loaded" }
    }

    protected val projectAsNode: ProjectAsNode get() = org.modelix.model.mpsadapters.mps.ProjectAsNode(mpsProject)

    protected open fun readDumpFromMPS() = readAction {
        projectAsNode.asData(includeChildren = false)
            .copy(
                id = null,
                role = null,
                children = mpsProject.projectModules.map { SModuleAsNode(it).asData() },
            )
    }

    protected open suspend fun readDumpFromServer(branchRef: BranchReference = defaultBranchRef): NodeData {
        return runWithNewConnection { client ->
            val versionOnServer = client.pull(branchRef, null)
            versionOnServer.getTree().asData()
        }
            .root // the node with ID ITree.ROOT_ID
    }

    suspend fun compareDumps(useInitialDump: Boolean = false, branchRef: BranchReference = defaultBranchRef) {
        compareDumps(
            if (useInitialDump) initialDumpFromMPS else readDumpFromMPS(),
            readDumpFromServer(branchRef),
        )
    }

    fun compareDumps(expected: NodeData, actual: NodeData) {
        assertEquals(
            json.encodeToString(expected.normalize()),
            json.encodeToString(actual.normalize()),
        )
    }

    protected fun runTestWithModelServer(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            install(ContentNegotiation) {
                json()
            }
            install(io.ktor.server.websocket.WebSockets)
            val repositoriesManager = RepositoriesManager(LocalModelClient(InMemoryStoreClient()))
            ModelReplicationServer(repositoriesManager).init(this)
            KeyValueLikeModelServer(repositoriesManager).init(this)
        }
        httpClient = client
        block()
    }

    protected fun runTestWithSyncService(body: suspend (ISyncService) -> Unit) = runTestWithModelServer {
        val syncService = ApplicationManager.getApplication().getService(ModelSyncService::class.java)
        try {
            this@SyncPluginTestBase.syncService = syncService
            syncService.registerProject(project)
            initialDumpFromMPS = readDumpFromMPS()
            body(syncService)
        } finally {
            syncService.unregisterProject(project)
            syncService.getConnections().forEach { Disposer.dispose(it) }
//            Disposer.dispose(syncService)
        }
    }

    override fun setUpProject() {
        runInEdtAndWait {
            super.setUpProject()
            @Suppress("removal")
            MPSCoreComponents.getInstance().getLibraryInitializer().load(
                listOf(
                    SetLibraryContributor.fromSet(
                        "repositoryconcepts",
                        setOf(
                            LibDescriptor(mpsProject.fileSystem.getFile(Path.of("repositoryconcepts").absolutePathString())),
                        ),
                    ),
                ),
            )
        }
    }

    override fun tearDown() {
        runInEdtAndWait {
            super.tearDown()
        }
    }

    override fun setUp() {
        runInEdtAndWait {
            super.setUp()
        }
    }

    override fun runInDispatchThread(): Boolean = false

    override fun isCreateDirectoryBasedProject(): Boolean = true

    override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path {
        val projectDir = super.getProjectDirOrFile(isDirectoryBasedProject)
        val testSpecificDataName = testDataName
            ?: getTestName(false).substringAfterLast("_with_", "").takeIf { it.isNotEmpty() }
        if (testSpecificDataName != null) {
            val sourceDir = File("testdata/$testSpecificDataName")
            sourceDir.copyRecursively(projectDir.toFile(), overwrite = true)
        }
        this.projectDir = projectDir
        return projectDir
    }

    protected suspend fun <R> runWithNewConnection(body: suspend (IModelClientV2) -> R): R {
        val client = ModelClientV2.builder().client(httpClient).url("http://localhost/v2/").build()
        client.init()
        return client.use { body(it) }
    }

    protected fun <R> writeAction(body: () -> R): R {
        return mpsProject.modelAccess.computeWriteAction(body)
    }

    protected fun <R> readAction(body: () -> R): R {
        var result: R? = null
        mpsProject.modelAccess.runReadAction {
            result = body()
        }
        return result as R
    }

    protected fun runTestWithProjectBinding(body: suspend (IBinding) -> Unit) = runTestWithSyncService {
        // initial sync to the server
        val projectBinding = syncService.connectServer(httpClient, Url("http://localhost/"))
            .newBranchConnection(defaultBranchRef)
            .bindProject(mpsProject, null)
        projectBinding.flush()
        body(projectBinding)
    }

    protected fun resolveMPSConcept(conceptFqName: String): SAbstractConcept {
        return resolveMPSConcept(conceptFqName.substringBeforeLast("."), conceptFqName.substringAfterLast("."))
    }

    protected fun resolveMPSConcept(languageName: String, conceptName: String): SAbstractConcept {
        val baseLanguage = LanguageRegistry.getInstance(mpsProject.repository).allLanguages.single { it.qualifiedName == languageName }
        val classConcept = baseLanguage.concepts.single { it.name == conceptName }
        check(classConcept !is InvalidConcept)
        return classConcept
    }

    protected fun SModel.createNode(conceptName: String): SNode {
        return createNode(resolveMPSConcept("jetbrains.mps.baseLanguage.ClassConcept") as SConcept)
    }

    protected fun SNode.setPropertyByName(name: String, value: String?) {
        setProperty(concept.properties.single { it.name == name }, value)
    }

    protected fun SModule.modelsWithoutDescriptor(): List<SModel> {
        return org.modelix.model.mpsplugin.SModuleUtils.getModelsWithoutDescriptor(this)
    }
}

private fun NodeData.normalize(): NodeData {
    val idMap = HashMap<String, String>()
    collectNodeIds(this, idMap)
    return normalizeNodeData(this, idMap)
}

private fun normalizeNodeData(node: NodeData, originalIds: MutableMap<String, String>): NodeData {
    var filteredChildren = node.children
    var filteredProperties = node.properties.minus(NodeData.ID_PROPERTY_KEY).minus(NodeData.ORIGINAL_NODE_ID_KEY)
    var replacedId = (originalIds[node.id] ?: node.id)
    var replacedRole = node.role
    when (node.concept) {
        "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313" -> { // project
            replacedRole = null
            filteredProperties -= "name"
            filteredProperties -= BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getUID()
        }
        "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895" -> { // Module
            // TODO remove this filter and fix the test
            filteredChildren = filteredChildren.filter { it.role == "models" }
                .sortedBy { it.properties["name"] }
                .sortedBy { it.properties[BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getUID()] }

            if (replacedId?.startsWith("mps-module:") == false && node.properties["id"] != null) {
                // TODO the name shouldn't be part of the ID
                replacedId = "mps-module:" + node.properties["id"] + "(" + node.properties["name"] + ")"
            }
        }
        "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242429" -> { // SingleLanguageDependency
            // TODO remove this filter and fix the test
            replacedId = null
        }
    }

    return node.copy(
        id = null, // replacedId,
        role = replacedRole,
        properties = filteredProperties.toSortedMap(),
        references = node.references.mapValues { originalIds[it.value] ?: it.value }.toSortedMap(),
        children = filteredChildren.map { normalizeNodeData(it, originalIds) }.sortedBy { it.role },
    )
}

private fun collectNodeIds(node: NodeData, idMap: MutableMap<String, String>) {
    val copyId = node.id
    val originalId = node.properties[NodeData.ORIGINAL_NODE_ID_KEY]
    if (originalId != null && copyId != null) {
        idMap[copyId] = originalId
    }
    node.children.forEach { collectNodeIds(it, idMap) }
}
