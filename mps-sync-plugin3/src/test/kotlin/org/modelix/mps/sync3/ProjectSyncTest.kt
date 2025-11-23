package org.modelix.mps.sync3

import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.application.ApplicationInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import jetbrains.mps.core.tool.environment.util.SetLibraryContributor
import jetbrains.mps.ide.MPSCoreComponents
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.library.contributor.LibDescriptor
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.MPSModuleRepository
import jetbrains.mps.smodel.ModelImports
import jetbrains.mps.smodel.SNodePointer
import jetbrains.mps.smodel.SNodeUtil
import jetbrains.mps.smodel.adapter.ids.SConceptId
import jetbrains.mps.smodel.adapter.ids.SContainmentLinkId
import jetbrains.mps.smodel.adapter.structure.concept.SConceptAdapterById
import jetbrains.mps.smodel.adapter.structure.link.SContainmentLinkAdapterById
import jetbrains.mps.vfs.VFSManager
import kotlinx.coroutines.delay
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.junit.Assert
import org.modelix.authorization.ModelixJWTUtil
import org.modelix.datastructures.model.ModelChangeEvent
import org.modelix.datastructures.model.MutationParameters
import org.modelix.datastructures.model.PropertyChangedEvent
import org.modelix.model.IVersion
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INodeReference
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getName
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.computeWriteOnModel
import org.modelix.model.client2.runWriteOnModel
import org.modelix.model.client2.runWriteOnTree
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.historyAsSequence
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.modelix.model.mpsadapters.MPSProjectReference
import org.modelix.model.mpsadapters.MPSProperty
import org.modelix.model.mpsadapters.computeRead
import org.modelix.model.mpsadapters.toMPS
import org.modelix.model.mpsadapters.toModelix
import org.modelix.model.mpsadapters.withoutDescriptorModel
import org.modelix.model.mutable.asModelSingleThreaded
import org.modelix.model.mutable.asReadOnlyModel
import org.modelix.model.oauth.IAuthConfig
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.SetPropertyOp
import org.modelix.model.server.ModelServerPermissionSchema
import org.modelix.mps.multiplatform.model.MPSIdGenerator
import org.modelix.mps.multiplatform.model.MPSModelReference
import org.modelix.mps.multiplatform.model.MPSNodeReference
import org.modelix.mps.sync3.ui.OpenFrontendAction
import org.modelix.streams.getBlocking
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.seconds

class ProjectSyncTest : ProjectSyncTestBase() {

    override fun setUp() {
        super.setUp()
    }

    override fun tearDown() {
        super.tearDown()
    }

    private suspend fun syncProjectToServer(
        testDataName: String,
        port: Int,
        branchRef: BranchReference,
        lastSyncedVersion: String? = null,
    ): IVersion {
        val project = openTestProject(testDataName)
        lastSnapshotBeforeSync = project.captureSnapshot()
        val service = IModelSyncService.getInstance(project)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef, lastSyncedVersion)
        val version = binding.flush()
        binding.close()
        lastSnapshotAfterSync = project.captureSnapshot()
        project.close()
        return version
    }

    fun `test initial sync with non-existing repository`(): Unit = runWithModelServer { port ->
        // The repository doesn't exist yet.
        val branchRef = RepositoryId("sync-test").getBranchReference()
        syncProjectToServer("initial", port, branchRef)

        // The initial sync should create the repository and push the MPS project to the server.
        val client = ModelClientV2.builder().url("http://localhost:$port").lazyAndBlockingQueries().build()
        val version = client.pull(branchRef, null)
        val rootNode = version.getModelTree().asModelSingleThreaded().getRootNode()
        val allNodes = rootNode.getDescendants(true)
        assertEquals(221, allNodes.count())
    }

    fun `test initial sync with existing empty repository`(): Unit = runWithModelServer { port ->
        // The repository is already initialized, but no data was written to it yet (empty history)
        val client = ModelClientV2.builder().url("http://localhost:$port").lazyAndBlockingQueries().build()
        val branchRef = RepositoryId("sync-test").getBranchReference()
        client.initRepository(branchRef.repositoryId)

        syncProjectToServer("initial", port, branchRef)

        // The initial sync should push the MPS project to the server.
        val version = client.pull(branchRef, null)
        val rootNode = version.getModelTree().asModelSingleThreaded().getRootNode()
        val allNodes = rootNode.getDescendants(true)
        assertEquals(221, allNodes.count())
    }

    fun `test initial sync with existing non-empty repository`(): Unit = runWithModelServer { port ->
        // The repository already exists and also some data was already written to it.
        val client = ModelClientV2.builder().url("http://localhost:$port").lazyAndBlockingQueries().build()
        val branchRef = RepositoryId("sync-test").getBranchReference()
        client.initRepository(branchRef.repositoryId)
        client.runWriteOnTree(branchRef, nodeIdGenerator = { MPSIdGenerator(client.getIdGenerator(), it) }) { tree ->
            val t = tree.getWriteTransaction()
            t.mutate(MutationParameters.Concept(t.tree.getRootNodeId(), BuiltinLanguages.MPSRepositoryConcepts.Repository.getReference()))
            t.mutate(
                MutationParameters.AddNew(
                    t.tree.getRootNodeId(),
                    BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference(),
                    -1,
                    listOf(MPSProjectReference("test-project") to BuiltinLanguages.MPSRepositoryConcepts.Project.getReference()),
                ),
            )
        }

        syncProjectToServer("initial", port, branchRef)

        // The initial sync should ignore the local data and reset the project to the remote version.
        val version = client.pull(branchRef, null)
        val rootNode = version.getModelTree().asModelSingleThreaded().getRootNode()
        val allNodes = rootNode.getDescendants(true)
        assertEquals(2, allNodes.count())
    }

    fun `test merge conflict`(): Unit = runWithModelServer { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference()
        val version1 = syncProjectToServer("initial", port, branchRef)
        println("version 1: $version1")
        val version2 = syncProjectToServer("change1", port, branchRef, lastSyncedVersion = version1.getContentHash())
        println("version 2: $version2")

        openTestProject("change1")
        val service = IModelSyncService.getInstance(mpsProject)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef, lastSyncedVersionHash = version1.getContentHash())
        val version4 = binding.flush()

        version1 as CLVersion
        version2 as CLVersion
        version4 as CLVersion

        val version3 = version4.historyAsSequence().filterIsInstance<CLVersion>()
            .first { it.isMerge().not() && it.baseVersion == version1 && it.getObjectHash() != version2.getObjectHash() }
        println("version 3: $version3")

        // version1 is the first one after the initial empty version
        val initialVersion = version1.baseVersion!!
        assertEquals(null, initialVersion.baseVersion)
        assertEquals(emptyList<IOperation>(), initialVersion.operations.toList())
        assertFalse(version1.isMerge())
        assertEquals(671, version1.numberOfOperations)

        // version2 applies some changes on top of version1
        assertEquals(version1, version2.baseVersion)
        assertFalse(version2.isMerge())
        assertEquals(449, version2.numberOfOperations)

        // version3 does the same as version2
        assertEquals(version1, version3.baseVersion)
        assertFalse(version3.isMerge())
        assertEquals(449, version3.numberOfOperations)
        assertEquals(version2.getTreeReference().getHash(), version3.getTreeReference().getHash())

        assertNotEquals(version2.getObjectHash(), version3.getObjectHash())

        // version4 is the result of merging version3 into version2
        val diff = version4.getModelTree().getChanges(version2.getModelTree(), false).toList().getBlocking(version4.getModelTree())
        assertEquals(emptyList<ModelChangeEvent<*>>(), diff)
        assertEquals(version2.getTreeReference().getHash(), version4.getTreeReference().getHash())

        assertFalse(version3.isMerge())
        assertEquals(version1, version3.baseVersion)
    }

    fun `test checkout into empty project`(): Unit = runWithModelServer { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference()
        syncProjectToServer("initial", port, branchRef)
        val expectedSnapshot = lastSnapshotBeforeSync

        val emptyProject = openTestProject(null)
        val service = IModelSyncService.getInstance(emptyProject)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef)
        binding.flush()

        assertEquals(expectedSnapshot, project.captureSnapshot())
    }

    fun `test write to new repo after checkout`(): Unit = runWithModelServer { port ->
        // An existing version on the server ...
        val branchRef1 = RepositoryId("sync-test-A").getBranchReference()
        syncProjectToServer("initial", port, branchRef1)

        // ... is checked out into an (empty) local project ...
        val emptyProject = openTestProject(null)
        val service = IModelSyncService.getInstance(emptyProject)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef1)
        binding.flush()

        readAction {
            assertEquals(5, mpsProject.projectModules.size)

            val allNodes = mpsProject.projectModules.asSequence()
                .map { MPSModuleAsNode(it) }
                .flatMap { it.getDescendants(true) }
            assertEquals(214, allNodes.count())
        }

        binding.close()

        // ... and then written back into a new repository
        val branchRef2 = RepositoryId("sync-test-B").getBranchReference()
        val binding2 = connection.bind(branchRef2)
        binding2.flush()

        suspend fun pullJson(ref: BranchReference) = connection
            .pullVersion(ref)
            .asNormalizedJson()

        // both repositories should now contain the same data
        assertEquals(pullJson(branchRef1), pullJson(branchRef2))
    }

    fun `test sync after MPS change`(): Unit = runWithModelServer { port ->
        // An MPS project is connected to a repository ...
        val branchRef = RepositoryId("sync-test").getBranchReference()
        openTestProject("initial")
        val service = IModelSyncService.getInstance(mpsProject)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef)
        val version1 = binding.flush()

        // ... and then an MPS user changes the name of a class ...
        val nameProperty = SNodeUtil.property_INamedConcept_name
        command {
            val node = mpsProject.projectModules
                .first { it.moduleName == "NewSolution" }
                .models
                .flatMap { it.rootNodes }
                .first { it.getProperty(nameProperty) == "MyClass" }
            println("will change property")
            node.setProperty(nameProperty, "Changed")
            println("property changed")
        }
        println("command done")

        val version2 = binding.flush()

        println("Version 1: $version1")
        println("Version 2: $version2")

        // ... which should result in a new version on the server with a single property change operation
        val changes: List<ModelChangeEvent<INodeReference>> = version2.getModelTree().let {
            it.getStreamExecutor().query { it.getChanges(version1.getModelTree(), false).toList() }
        }
        assertEquals(1, changes.size)
        val change = changes.single() as PropertyChangedEvent<INodeReference>
        assertEquals(MPSProperty(nameProperty).getUID(), change.role.getUID())
        assertEquals("MyClass", version1.getModelTree().getProperty(change.nodeId, change.role).getBlocking(version1.getModelTree()))
        assertEquals("Changed", version2.getModelTree().getProperty(change.nodeId, change.role).getBlocking(version1.getModelTree()))
    }

    fun `test descendants of new node are synchronized`() = runChangeInMpsTest { classNode ->
        val memberRole = SContainmentLinkAdapterById(SContainmentLinkId.deserialize("f3061a53-9226-4cc5-a443-f952ceaf5816/1107461130800/5375687026011219971"), "member")
        val visibilityRole = SContainmentLinkAdapterById(SContainmentLinkId.deserialize("f3061a53-9226-4cc5-a443-f952ceaf5816/1178549954367/1178549979242"), "member")
        val bodyRole = SContainmentLinkAdapterById(SContainmentLinkId.deserialize("f3061a53-9226-4cc5-a443-f952ceaf5816/1178549954367/1068580123135"), "body")
        val statementRole = SContainmentLinkAdapterById(SContainmentLinkId.deserialize("f3061a53-9226-4cc5-a443-f952ceaf5816/1178549954367/1068581517665"), "statement")
        val instanceMethodDeclarationConcept = SConceptAdapterById(SConceptId.deserialize("f3061a53-9226-4cc5-a443-f952ceaf5816/1068580123165"), "InstanceMethodDeclaration")
        val publicVisibilityConcept = SConceptAdapterById(SConceptId.deserialize("f3061a53-9226-4cc5-a443-f952ceaf5816/1146644602865"), "PublicVisibility")
        val statementListConcept = SConceptAdapterById(SConceptId.deserialize("f3061a53-9226-4cc5-a443-f952ceaf5816/1068580123136"), "StatementList")
        val returnStatementConcept = SConceptAdapterById(SConceptId.deserialize("f3061a53-9226-4cc5-a443-f952ceaf5816/1068581242878"), "ReturnStatement")

        val methodNode = jetbrains.mps.smodel.SNode(instanceMethodDeclarationConcept)
        val visibilityNode = jetbrains.mps.smodel.SNode(publicVisibilityConcept).also { methodNode.addChild(visibilityRole, it) }
        val statementListNode = jetbrains.mps.smodel.SNode(statementListConcept).also { methodNode.addChild(bodyRole, it) }
        val returnStatementNode = jetbrains.mps.smodel.SNode(returnStatementConcept).also { statementListNode.addChild(statementRole, it) }

        // adding the new method when it already contains all the descendants will result in a single change event.
        // There is no event for the other `addChild` calls, which is what this test is about.
        classNode.addChild(memberRole, methodNode)
    }

    private fun runChangeInMpsTest(mutator: (SNode) -> Unit): Unit = runWithModelServer { port ->
        // An MPS project is connected to a repository ...
        val branchRef = RepositoryId("sync-test").getBranchReference()
        openTestProject("initial")
        val binding1 = IModelSyncService.getInstance(mpsProject).addServer("http://localhost:$port").bind(branchRef)
        val version1 = binding1.flush()
        val snapshot1 = project.captureSnapshot()

        // ... and then an MPS user changes something in MPS ...
        command {
            val nameProperty = SNodeUtil.property_INamedConcept_name
            val node = mpsProject.projectModules
                .first { it.moduleName == "NewSolution" }
                .models
                .flatMap { it.rootNodes }
                .first { it.getProperty(nameProperty) == "MyClass" }
            mutator(node)
        }

        // ... which is synchronized to the server.
        val version2 = binding1.flush()
        val snapshot2 = project.captureSnapshot()
        project.close()

        // A second MPS client should end up in the same state.

        val branchRef2 = branchRef.repositoryId.getBranchReference("branchB")
        val client = ModelClientV2.builder().url("http://localhost:$port").build()
        client.push(branchRef2, version1, null)

        openTestProject("initial")
        val binding2 = IModelSyncService.getInstance(mpsProject).addServer("http://localhost:$port").bind(branchRef2)
        binding2.flush()
        assertEquals(snapshot1, project.captureSnapshot())
        client.push(branchRef2, version2, version1)
        binding2.flush()
        assertEquals(snapshot2, project.captureSnapshot())
    }

    fun `test sync after model-server change`(): Unit = runWithModelServer { port ->
        // An MPS project is connected to a repository ...
        val branchRef = RepositoryId("sync-test").getBranchReference()
        openTestProject("initial")
        val service = IModelSyncService.getInstance(mpsProject)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef)
        val version1 = binding.flush()

        val nameProperty = MPSProperty(SNodeUtil.property_INamedConcept_name)
        val mpsNode = readAction {
            mpsProject.projectModules
                .first { it.moduleName == "NewSolution" }
                .models
                .flatMap { it.rootNodes }
                .first { it.getProperty(nameProperty.property) == "MyClass" }
        }

        assertEquals("MyClass", readAction { mpsNode.getProperty(nameProperty.property) })

        // ... and then some non-MPS client changes a property ...
        val client = ModelClientV2.builder().url("http://localhost:$port").lazyAndBlockingQueries().build().also { it.init() }
        client.runWriteOnModel(branchRef) { rootNode ->
            val node = rootNode.getDescendants(true)
                .first { it.getPropertyValue(nameProperty.toReference()) == "MyClass" }
            node.setPropertyValue(nameProperty.toReference(), "Changed")
        }
        val version2 = binding.flush()

        // ... which should then be visible in MPS
        assertEquals("Changed", readAction { mpsNode.getProperty(nameProperty.property) })
    }

    fun `test new node on model-server`(): Unit = runWithModelServer { port ->
        // An MPS project is connected to a repository ...
        val branchRef = RepositoryId("sync-test").getBranchReference()
        openTestProject("initial")
        val service = IModelSyncService.getInstance(mpsProject)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef)
        val version1 = binding.flush()

        val nameProperty = MPSProperty(SNodeUtil.property_INamedConcept_name)
        val mpsNode = writeAction {
            mpsProject.projectModules
                .first { it.moduleName == "NewSolution" }
                .models
                .flatMap { it.rootNodes }
                .first { it.getProperty(nameProperty.property) == "MyClass" }
        }

        assertEquals("MyClass", readAction { mpsNode.getProperty(nameProperty.property) })

        // ... and then a non-MPS client adds a new node ...
        val client = ModelClientV2.builder().url("http://localhost:$port").lazyAndBlockingQueries().build().also { it.init() }
        val (_, newNodeIdOnServer) = client.computeWriteOnModel(branchRef, { MPSIdGenerator(client.getIdGenerator(), it) }) { rootNode ->
            val node = rootNode.getDescendants(true)
                .first { it.getPropertyValue(nameProperty.toReference()) == "MyClass" }
            val node2 = node.getParent()!!.addNewChild(node.getContainmentLink(), -1, node.getConceptReference())
            node2.setPropertyValue(nameProperty.toReference(), "NewClass")
            node2.getNodeReference().serialize()
        }
        val version2 = binding.flush()

        // ... which should then be added in MPS ...
        readAction {
            val siblings = mpsNode.model!!.rootNodes
            val newNode = siblings.first { it.getProperty(nameProperty.property) == "NewClass" }
            assertEquals("NewClass", newNode.getProperty(nameProperty.property))
            // ... and have an ID that isn't a random one generated by MPS
            assertEquals(newNodeIdOnServer, newNode.reference.toModelix().serialize())
        }
    }

    fun `test sync after reconnect ignoring local`(): Unit = runWithModelServer { port ->
        // A version exists on the server ...
        val branchRef = RepositoryId("sync-test").getBranchReference()
        val version1 = syncProjectToServer("initial", port, branchRef)

        // ... and then some other existing MPS project is connected to that repository ...
        val version2 = syncProjectToServer("change1", port, branchRef)

        // ... and since it's an unrelated project, it should just be overwritten
        assertEquals(version1.getContentHash(), version2.getContentHash())
    }

    fun `test sync after reconnect merging local`(): Unit = runWithModelServer { port ->
        // A version exists on the server ...
        val branchRef = RepositoryId("sync-test-A").getBranchReference()
        val version1 = syncProjectToServer("initial", port, branchRef)

        // ... and while being disconnected, some changes are made in MPS ...

        // ... and then the project is connected again ...
        val version2 = syncProjectToServer("change1", port, branchRef, version1.getContentHash())

        // ... causing the changes to be commited on top of the remote version ...
        Assert.assertNotEquals(version1.getContentHash(), version2.getContentHash())
        assertEquals(version1.getContentHash(), (version2 as CLVersion).baseVersion?.getContentHash())

        val branchRef2 = RepositoryId("sync-test-B").getBranchReference()
        val expected = syncProjectToServer("change1", port, branchRef2)

        // ... and the new version should contain the state of the project before the reconnect.
        assertEquals(expected.asNormalizedJson(), version2.asNormalizedJson())
    }

    fun `test sync to MPS after non-trivial commit at startup`(): Unit = runWithModelServer { port ->
        // Two clients are in sync with the same version ...
        val branchRef = RepositoryId("sync-test").getBranchReference()
        val version1 = syncProjectToServer("initial", port, branchRef)

        // ... and while one client is disconnected, the other client continues making changes.
        val version2 = syncProjectToServer("change1", port, branchRef, version1.getContentHash())
        val expectedSnapshot = lastSnapshotBeforeSync

        println("initial two versions pushed")

        // The second client then reconnects ...
        openTestProject("initial")
        println("initial project opened")
        val binding = IModelSyncService.getInstance(mpsProject)
            .addServer("http://localhost:$port")
            .bind(branchRef, version1.getContentHash())
        println("binding created")
        val version3 = binding.flush()

        // ... applies all the pending changes and is again in sync with the other client
        assertEquals(expectedSnapshot, project.captureSnapshot())
    }

    fun `test sync to MPS after non-trivial commit with active binding`(): Unit = runWithModelServer { port ->
        // Two clients are in sync with the same version ...
        val branchRef = RepositoryId("sync-test").getBranchReference("branchA")
        val version1 = syncProjectToServer("initial", port, branchRef)

        // ... and while one client is disconnected, the other client continues making changes.
        val version2 = syncProjectToServer("change1", port, branchRef, version1.getContentHash())
        val expectedSnapshot = lastSnapshotBeforeSync

        println("initial two versions pushed")

        val branchRef2 = branchRef.repositoryId.getBranchReference("branchB")
        val client = ModelClientV2.builder().url("http://localhost:$port").lazyAndBlockingQueries().build()
        client.push(branchRef2, version1, null)

        // The second client then reconnects ...
        openTestProject("initial")
        val snap1 = project.captureSnapshot()
        val binding = IModelSyncService.getInstance(mpsProject)
            .addServer("http://localhost:$port")
            .bind(branchRef2, null)
        binding.flush()
        assertEquals(snap1, project.captureSnapshot())

        client.push(branchRef2, version2, version1)
        binding.flush()

        // ... applies all the pending changes and is again in sync with the other client
        assertEquals(expectedSnapshot, project.captureSnapshot())
    }

    /**
     * This reproduces a bug observed in the SECURE project.
     */
    fun `test sync of model dependencies`(): Unit = runWithModelServer { port ->
        // An initialized repository exists on the server.
        val branchRef = RepositoryId("sync-test").getBranchReference("branchA")
        val version1 = syncProjectToServer("model-dependencies", port, branchRef)

        // The repository is then checked out by an MPS client using a modelix.xml without a commit hash.
        openTestProject(null) { projectDir ->
            projectDir.resolve(".mps").resolve("modelix.xml").writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                  <component name="modelix-sync">
                    <binding>
                      <enabled>true</enabled>
                      <url>http://localhost:$port</url>
                      <repository>${branchRef.repositoryId.id}</repository>
                      <branch>${branchRef.branchName}</branch>
                    </binding>
                  </component>
                </project>
                """.trimIndent(),
            )
        }
        val connections = IModelSyncService.getInstance(mpsProject)
            .getServerConnections()
        assertEquals(1, connections.flatMap { it.getBindings() }.count { it.isEnabled() })
        connections.flatMap { it.getBindings() }.forEach { it.flushIfEnabled() }

        // The repository is expected to remain unchanged.
        val client = ModelClientV2.builder().url("http://localhost:$port").lazyAndBlockingQueries().build()
        assertEquals(version1.getContentHash(), client.pull(branchRef, null).getContentHash())

        // The following bug was observed:
        // Dependencies to local models that are not stored on the server are not properly created,
        // even though the dependency itself exist on the server.
        //
        // It was also observed that this might be the case for dependencies that involve a devkit.

        val mpsModels = readAction {
            mpsProject
                .projectModules
                .flatMap {
                    when (it) {
                        is Language -> listOf(it) + it.generators
                        else -> listOf(it)
                    }
                }
                .flatMap { it.models }
                .withoutDescriptorModel()
                .associateBy { it.name.longName }
        }
        val serverModels = version1.getModelTree().asReadOnlyModel().getRootNode().getDescendants(true)
            .filter { it.getConceptReference() == BuiltinLanguages.MPSRepositoryConcepts.Model.getReference() }
            .associateBy { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()).orEmpty() }

        assertEquals(serverModels.keys, mpsModels.keys)

        for (modelName in mpsModels.keys) {
            val mpsModel = mpsModels.getValue(modelName)
            val serverModel = serverModels.getValue(modelName)

            val importsInMPS = readAction { ModelImports(mpsModel).importedModels.toSet() }
            val importsOnServer = serverModel.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports.toReference())
                .mapNotNull { it.getReferenceTargetRef(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model.toReference()) }
                .mapNotNull { MPSModelReference.tryConvert(it)?.toMPS() }
                .toSet()
            assertEquals(importsOnServer, importsInMPS)
        }
    }

    private fun getMPSPlatformVersion(): Int {
        val info = ApplicationInfo.getInstance()
        return (info.majorVersion.toInt() - 2000) * 10 + info.minorVersionMainPart.toInt()
    }

    fun `test change in stub model`(): Unit = runWithModelServer { port ->
        if (getMPSPlatformVersion() <= 211) return@runWithModelServer // not worth maintaining these old versions

        // ensure stub model loading works
        openTestProject("with-stub-models").also { p ->
            val mpsProject = ProjectHelper.fromIdeaProject(p)!!
            mpsProject.modelAccess.runReadAction {
                val module = mpsProject.projectModules.first { it.moduleName == "SolutionWithJavaSourceStubs" }
                assertEquals(listOf("org.modelix.sync.sandbox@java_stub"), module.models.map { it.name.value })
            }
            p.close()
        }

        val branchRef = RepositoryId("sync-test").getBranchReference("branchA")
        val version1 = syncProjectToServer("with-stub-models", port, branchRef)

        val version2 = syncProjectToServer("with-stub-models-changed", port, branchRef, version1.getContentHash())
        // stub models are ignored and changes in them have no effect
        assertEquals(version1.getContentHash(), version2.getContentHash())
    }

    private suspend fun <R> withGlobalLibrary(folder: File, body: suspend () -> R): R {
        val vfsManager = MPSCoreComponents.getInstance().platform.findComponent(VFSManager::class.java)!!
        val packagedModulesFolder = vfsManager.getFileSystem(VFSManager.FILE_FS).getFile(folder)
        val packagedModulesContributors = listOf(SetLibraryContributor.fromSet("packaged-modules", setOf(LibDescriptor(packagedModulesFolder))))
        MPSCoreComponents.getInstance().libraryInitializer.load(packagedModulesContributors)
        try {
            return body()
        } finally {
            MPSCoreComponents.getInstance().libraryInitializer.unload(packagedModulesContributors)
        }
    }

    fun `test change in packaged module`(): Unit = runWithModelServer { port ->
        // Modules in packaged-modules/modules.jar are the same as in solutions/ but packaged into a jar file
        // which makes them read-only.

        val branchRef = RepositoryId("sync-test").getBranchReference("branchA")
        val changedNodeRef = SNodePointer.deserialize("r:cd78e6ac-0e34-490a-9b49-e5643f948d6d(NewSolution.a_model)/8281020627045237343")
        val version1 = withGlobalLibrary(File("testdata/with-packaged-module/packaged-modules")) {
            val globalRepo = MPSModuleRepository.getInstance()
            globalRepo.modelAccess.runReadAction {
                assertContains(globalRepo.modules.map { it.moduleName.orEmpty() }.filterNot { it.startsWith("jetbrains.") }, "NewSolution")
            }

            openTestProject("with-packaged-module").also { p ->
                val mpsProject = ProjectHelper.fromIdeaProject(p)!!
                mpsProject.modelAccess.runReadAction {
                    val changedNode = checkNotNull(changedNodeRef.resolve(mpsProject.repository)) {
                        "Node not found: $changedNodeRef"
                    }
                    assertEquals("MyClass", changedNode.name)
                    assertEquals("NewSolution.a_model", changedNode.model?.name?.value)
                    assertTrue("Not read-only: ${changedNode.model?.name}", changedNode.model?.isReadOnly == true)
                }
                p.close()
            }

            syncProjectToServer("with-packaged-module", port, branchRef)
        }

        delay(1.seconds) // wait for the modules to be unregistered

        val version2 = syncProjectToServer("with-packaged-module-changed", port, branchRef, version1.getContentHash())

        // read-only modules should be skipped
        assertNotEquals(version1.getContentHash(), version2.getContentHash())
        version2 as CLVersion
        val operation = version2.operations.single() as SetPropertyOp
        assertEquals("MyClassRenamed", operation.value)

        val branchRef2 = RepositoryId("sync-test").getBranchReference("branchB")
        val client = ModelClientV2.builder().url("http://localhost:$port").lazyAndBlockingQueries().build()
        client.push(branchRef2, version1, null)

        withGlobalLibrary(File("testdata/with-packaged-module/packaged-modules")) {
            openTestProject("with-packaged-module")
            val binding = IModelSyncService.getInstance(mpsProject)
                .addServer("http://localhost:$port")
                .bind(branchRef, null)
            binding.flush()

            suspend fun nameOnServer() = client.pull(branchRef2, null).getModelTree().asModelSingleThreaded().resolveNode(MPSNodeReference.parseSNodeReference(changedNodeRef.toString())).getName()
            fun nameInMPS() = mpsProject.repository.computeRead { changedNodeRef.resolve(mpsProject.repository)!!.name }

            assertEquals("MyClass", nameInMPS())
            MPSNodeReference.parseSNodeReference(changedNodeRef.toString())
            assertEquals("MyClass", nameOnServer())
            client.push(branchRef2, version2, null)
            assertEquals("MyClassRenamed", nameOnServer())
            binding.flush()
            assertEquals("MyClass", nameInMPS()) // name remains unchanged, because the model is read-only
            assertEquals("MyClassRenamed", nameOnServer())
        }
    }

    fun `test missing permission on initial sync is detected`(): Unit = runWithModelServer(hmacKey = "abc") { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference("branchA")
        AppLevelModelSyncService.getInstance().getOrCreateConnection(
            ModelServerConnectionProperties(
                url = "http://localhost:$port",
                repositoryId = branchRef.repositoryId,
            ),
        ).setAuthorizationConfig(
            IAuthConfig.fromTokenProvider {
                ModelixJWTUtil().also { it.setHmac512Key("abc") }
                    .createAccessToken("sync-plugin-test@modelix.org", listOf())
            },
        )

        openTestProject("initial")
        val binding = IModelSyncService.getInstance(mpsProject)
            .addServer("http://localhost:$port")
            .bind(branchRef, null)
        assertFailsWith<ResponseException> {
            binding.flush()
        }

        assertEquals(IBinding.Status.NoPermission(user = "sync-plugin-test@modelix.org"), binding.getStatus())
    }

    fun `test missing permission after initial sync is detected`(): Unit = runWithModelServer(hmacKey = "abc") { port ->
        fun now() = kotlinx.datetime.Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val leeway = 60.seconds
        var grantPermission = true
        var tokenExpiration = now()
        val branchRef = RepositoryId("sync-test").getBranchReference("branchA")

        fun initConnection(repositoryId: RepositoryId?) {
            AppLevelModelSyncService.getInstance().getOrCreateConnection(
                ModelServerConnectionProperties(
                    url = "http://localhost:$port",
                    repositoryId = repositoryId,
                ),
            ).setAuthorizationConfig(
                IAuthConfig.fromTokenProvider {
                    val permissions = listOfNotNull(
                        ModelServerPermissionSchema.repository(branchRef.repositoryId).write.fullId.takeIf { grantPermission },
                    )
                    ModelixJWTUtil()
                        .also { it.setHmac512Key("abc") }
                        .createAccessToken("sync-plugin-test@modelix.org", permissions) {
                            tokenExpiration = now() + 10.seconds
                            it.claim("exp", (tokenExpiration - leeway).epochSeconds)
                        }
                        .also { println("Generated token: $it") }
                },
            )
        }
        initConnection(null)
        initConnection(branchRef.repositoryId)

        openTestProject("initial")
        val binding = IModelSyncService.getInstance(mpsProject)
            .addServer("http://localhost:$port")
            .bind(branchRef, null)
        binding.flush()
        println("Initial sync done")
        grantPermission = false

        delay(tokenExpiration - now())

        val nameProperty = SNodeUtil.property_INamedConcept_name
        command {
            val node = mpsProject.projectModules
                .first { it.moduleName == "NewSolution" }
                .models
                .flatMap { it.rootNodes }
                .first { it.getProperty(nameProperty) == "MyClass" }
            println("will change property")
            node.setProperty(nameProperty, "Changed")
            println("property changed")
        }

        println(AppLevelModelSyncService.getInstance().getConnections().map { it.properties })
        assertFailsWith<ResponseException> {
            binding.flush()
        }

        assertEquals(IBinding.Status.NoPermission(user = "sync-plugin-test@modelix.org"), binding.getStatus())
    }

    fun `test default frontend url`(): Unit = runWithModelServer { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference("branchA")

        openTestProject("initial")
        val binding = IModelSyncService.getInstance(mpsProject)
            .addServer("http://localhost:$port")
            .bind(branchRef, null)

        val action = OpenFrontendAction()
        val urls = action.getFrontendUrls(project)
        assertEquals(listOf("http://localhost:$port/v2/repositories/sync-test/branches/branchA/frontend"), urls)

        val response = HttpClient(CIO) {
            followRedirects = false
        }.get(urls.single())
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("http://localhost:$port/history/sync-test/branchA/", URL(urls.single()).toURI().resolve(response.headers["Location"]).toString())
    }

    fun `test configured frontend url`(): Unit = runWithModelServer(additionalEnv = mapOf("MODELIX_FRONTEND_URL" to "http://my-frontend.example.com/{repository}/{branch}/overview")) { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference("branchA")

        openTestProject("initial")
        val binding = IModelSyncService.getInstance(mpsProject)
            .addServer("http://localhost:$port")
            .bind(branchRef, null)

        val action = OpenFrontendAction()
        val urls = action.getFrontendUrls(project)
        assertEquals(listOf("http://localhost:$port/v2/repositories/sync-test/branches/branchA/frontend"), urls)

        val response = HttpClient(CIO) {
            followRedirects = false
        }.get(urls.single())
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("http://my-frontend.example.com/sync-test/branchA/overview", URL(urls.single()).toURI().resolve(response.headers["Location"]).toString())
    }

    fun `test loading enabled persisted binding`(): Unit = runPersistedBindingTest(true)
    fun `test loading disabled persisted binding`(): Unit = runPersistedBindingTest(false)

    fun runPersistedBindingTest(enabled: Boolean) = runWithModelServer { port ->
        // The client is in sync ...
        val branchRef = RepositoryId("sync-test").getBranchReference()
        val version1 = syncProjectToServer("initial", port, branchRef)
        val snapshot1 = lastSnapshotBeforeSync

        // ... and then closes the project while some other client continues making changes.
        val version2 = syncProjectToServer("change1", port, branchRef, version1.getContentHash())
        val snapshot2 = lastSnapshotBeforeSync
        val expectedSnapshot = if (enabled) snapshot2 else snapshot1

        // Then the client opens the project again and reconnects using the persisted binding information.
        openTestProject("initial") { projectDir ->
            projectDir.resolve(".mps").resolve("modelix.xml").writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                  <component name="modelix-sync">
                    <binding>
                      <enabled>$enabled</enabled>
                      <url>http://localhost:$port</url>
                      <repository>${branchRef.repositoryId.id}</repository>
                      <branch>${branchRef.branchName}</branch>
                      <versionHash>${version1.getContentHash()}</versionHash>
                    </binding>
                  </component>
                </project>
                """.trimIndent(),
            )
        }

        val binding = IModelSyncService.getInstance(mpsProject).getBindings().single()
        assertEquals(branchRef, binding.getBranchRef())
        if (enabled) {
            val version3 = binding.flush()

            assertEquals(version2.getContentHash(), version3.getContentHash())

            // ... applies all the pending changes and is again in sync with the other client
            assertEquals(expectedSnapshot, project.captureSnapshot())
        } else {
            assertFailsWith<IllegalStateException> {
                binding.flush()
            }
        }
    }

    fun `test saving binding state`(): Unit = runWithModelServer { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference()
        openTestProject(null)
        val binding = IModelSyncService.getInstance(project).addServer("http://localhost:$port").bind(branchRef)
        val version1 = binding.flush()
        saveSettings(project, true)
        val actual = Path.of(project.basePath).resolve(".mps").resolve("modelix.xml").readText()
        val expected = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project version="4">
              <component name="modelix-sync">
                <binding>
                  <enabled>true</enabled>
                  <url repositoryScoped="false">http://localhost:$port</url>
                  <repository>${branchRef.repositoryId.id}</repository>
                  <branch>${branchRef.branchName}</branch>
                  <versionHash>${version1.getContentHash()}</versionHash>
                </binding>
              </component>
            </project>
        """.trimIndent()
        assertEquals(expected, actual)
    }

    fun `test binding can be disabled`(): Unit = runWithModelServer { port ->
        // An MPS project is connected to a repository ...
        val branchRef = RepositoryId("sync-test").getBranchReference()
        openTestProject("initial")
        val service = IModelSyncService.getInstance(mpsProject)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef)
        val version1 = binding.flush()

        // With the binding disabled ...
        assertTrue(binding.isEnabled())
        binding.disable()
        assertFalse(binding.isEnabled())

        // ... the MPS user changes the name of a class ...
        val nameProperty = SNodeUtil.property_INamedConcept_name
        command {
            val node = PersistenceFacade.getInstance()
                .createNodeReference("r:cd78e6ac-0e34-490a-9b49-e5643f948d6d(NewSolution.a_model)/8281020627045237343")
                .resolve(mpsProject.repository)!!
            node.setProperty(nameProperty, "A")
        }

        binding.flushIfEnabled()

        val version2 = connection.pullVersion(branchRef)

        // ... which should not be synchronized to the server
        assertEquals(version1.getContentHash(), version2.getContentHash())

        binding.enable()
        val version3 = binding.flush()
    }

    fun `test sync projects with different name`(): Unit = runWithModelServer { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference()
        syncProjectToServer("initial", port, branchRef)
        val expectedSnapshot = lastSnapshotBeforeSync

        openTestProject(null, projectName = "project-with-different-name")
        assertEquals("project-with-different-name", project.name)

        val binding = IModelSyncService.getInstance(mpsProject)
            .addServer("http://localhost:$port")
            .bind(branchRef)
        binding.flush()

        assertEquals("test-project", project.name)
        assertEquals(expectedSnapshot, project.captureSnapshot())
    }

    fun `test no change at startup doesn't create a new version`(): Unit = runWithModelServer { port ->
        // A client was previously in sync with the server ...
        val branchRef = RepositoryId("sync-test").getBranchReference()
        val version1 = syncProjectToServer("initial", port, branchRef)
        val expectedSnapshot = lastSnapshotBeforeSync

        // ... and then MPS is restart. No change happened.
        openTestProject("initial")
        val binding = IModelSyncService.getInstance(mpsProject)
            .addServer("http://localhost:$port")
            .bind(branchRef, version1.getContentHash())
        val version2 = binding.flush()

        // The client shouldn't push any changes ...
        assertEquals(version1.getContentHash(), version2.getContentHash())

        // ... and it shouldn't pull any changes.
        assertEquals(expectedSnapshot, project.captureSnapshot())
    }

    private fun NodeData.normalizeIds(): NodeData {
        val idMap = HashMap<String, String>()
        fillIdSubstitutions(idMap, AtomicLong())
        return replaceIds(idMap)
    }

    private fun NodeData.replaceIds(idMap: MutableMap<String, String>): NodeData {
        fun replaceId(id: String) = idMap[id] ?: id

        return copy(
            id = id?.let { replaceId(it) },
            children = children.map { it.replaceIds(idMap) },
            references = references.mapValues { replaceId(it.value) },
        )
    }

    private fun NodeData.sortChildren(): NodeData {
        return copy(
            children = children.sortedWith(compareBy({ it.role }, { it.id })).map { it.sortChildren() },
        )
    }

    private fun NodeData.fillIdSubstitutions(idMap: MutableMap<String, String>, idGenerator: AtomicLong) {
        id?.let {
            idMap.getOrPut(it) {
                properties[NodeData.ID_PROPERTY_KEY] ?: ("normalized:" + idGenerator.incrementAndGet())
            }
        }
        children.forEach { it.fillIdSubstitutions(idMap, idGenerator) }
    }

    private fun IVersion.asNormalizedJson(): String {
        return getModelTree()
            .asModelSingleThreaded()
            .getRootNode()
            .asLegacyNode()
            .asData()
            .copy(id = "root")
//            .normalizeIds()
            .sortChildren()
            .toJson()
    }
}
