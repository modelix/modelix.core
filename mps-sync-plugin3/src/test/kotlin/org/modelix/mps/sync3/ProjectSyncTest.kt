package org.modelix.mps.sync3

import com.badoo.reaktive.observable.toList
import com.intellij.configurationStore.saveSettings
import com.intellij.testFramework.TestApplicationManager
import jetbrains.mps.smodel.SNodeUtil
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert
import org.modelix.model.IVersion
import org.modelix.model.api.TreePointer
import org.modelix.model.api.async.PropertyChangedEvent
import org.modelix.model.api.async.TreeChangeEvent
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.api.key
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWriteOnBranch
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.modelix.model.mpsadapters.MPSProperty
import org.modelix.model.mpsadapters.tryDecodeModelixReference
import org.modelix.streams.getSuspending
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.absolute
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

private val modelServerDir = Path.of("../model-server").absolute().normalize()
private val modelServerImage = ImageFromDockerfile()
    .withDockerfile(modelServerDir.resolve("Dockerfile"))

class ProjectSyncTest : MPSTestBase() {

    private var lastSnapshotBeforeSync: String? = null
    private var lastSnapshotAfterSync: String? = null

    override fun setUp() {
        super.setUp()
        TestApplicationManager.getInstance()
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

    fun `test initial sync to server`(): Unit = runWithModelServer { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference()
        syncProjectToServer("initial", port, branchRef)

        val client = ModelClientV2.builder().url("http://localhost:$port").build()
        val version = client.pull(branchRef, null)
        val rootNode = TreePointer(version.getTree()).getRootNode()
        val allNodes = rootNode.getDescendants(true)
        assertEquals(221, allNodes.count())
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
        val changes: List<TreeChangeEvent> = version2.getTree().asAsyncTree().getChanges(version1.getTree().asAsyncTree(), false).toList().getSuspending()
        assertEquals(1, changes.size)
        val change = changes.single() as PropertyChangedEvent
        assertEquals(MPSProperty(nameProperty).getUID(), change.role.getUID())
        assertEquals("MyClass", version1.getTree().getProperty(change.nodeId, change.role.key(version1.getTree())))
        assertEquals("Changed", version2.getTree().getProperty(change.nodeId, change.role.key(version1.getTree())))
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
        val client = ModelClientV2.builder().url("http://localhost:$port").build().also { it.init() }
        client.runWriteOnBranch(branchRef) { branch ->
            val node = branch.getRootNode().getDescendants(true)
                .first { it.getPropertyValue(nameProperty) == "MyClass" }
            node.setPropertyValue(nameProperty, "Changed")
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
        val client = ModelClientV2.builder().url("http://localhost:$port").build().also { it.init() }
        val newNodeIdOnServer = client.runWriteOnBranch(branchRef) { branch ->
            val node = branch.getRootNode().getDescendants(true)
                .first { it.getPropertyValue(nameProperty) == "MyClass" }
                .asWritableNode()
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
            assertEquals(newNodeIdOnServer, newNode.nodeId.tryDecodeModelixReference()?.serialize())
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

    fun `test sync to MPS after non-trivial commit`(): Unit = runWithModelServer { port ->
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

    fun `test loading persisted binding`(): Unit = runWithModelServer { port ->
        // The client is in sync ...
        val branchRef = RepositoryId("sync-test").getBranchReference()
        val version1 = syncProjectToServer("initial", port, branchRef)

        // ... and then closes the project while some other client continues making changes.
        val version2 = syncProjectToServer("change1", port, branchRef, version1.getContentHash())
        val expectedSnapshot = lastSnapshotBeforeSync

        // Then the client opens the project again and reconnects using the persisted binding information.
        openTestProject("initial") { projectDir ->
            projectDir.resolve(".mps").resolve("modelix.xml").writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                  <component name="modelix-sync">
                    <binding>
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

        val binding = IModelSyncService.getInstance(mpsProject).getServerConnections().flatMap { it.getBindings() }.single()
        assertEquals(branchRef, binding.branchRef)
        val version3 = binding.flush()

        assertEquals(version2.getContentHash(), version3.getContentHash())

        // ... applies all the pending changes and is again in sync with the other client
        assertEquals(expectedSnapshot, project.captureSnapshot())
    }

    fun `test storing persisted binding`(): Unit = runWithModelServer { port ->
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
                  <url>http://localhost:$port</url>
                  <repository>${branchRef.repositoryId.id}</repository>
                  <branch>${branchRef.branchName}</branch>
                  <versionHash>${version1.getContentHash()}</versionHash>
                </binding>
              </component>
            </project>
        """.trimIndent()
        assertEquals(expected, actual)
    }

    private fun runWithModelServer(body: suspend (port: Int) -> Unit) = runBlocking {
        withTimeout(3.minutes) {
            val modelServer: GenericContainer<*> = GenericContainer(modelServerImage)
                .withExposedPorts(28101)
                .withCommand("-inmemory")
                .waitingFor(Wait.forListeningPort().withStartupTimeout(3.minutes.toJavaDuration()))
                .withLogConsumer {
                    println(it.utf8StringWithoutLineEnding)
                }

            modelServer.start()
            try {
                body(modelServer.firstMappedPort)
            } finally {
                modelServer.stop()
            }
        }
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
        return getTree()
            .let { TreePointer(it) }
            .getRootNode()
            .asData()
            .normalizeIds()
            .sortChildren()
            .toJson()
    }
}
