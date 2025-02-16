package org.modelix.mps.sync3

import com.badoo.reaktive.observable.toList
import com.intellij.testFramework.TestApplicationManager
import jetbrains.mps.smodel.SNodeUtil
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

private val modelServerDir = Path.of("../model-server").absolute().normalize()
private val modelServerImage = ImageFromDockerfile()
    .withDockerfile(modelServerDir.resolve("Dockerfile"))

class ProjectSyncTest : MPSTestBase() {

    override fun setUp() {
        super.setUp()
        TestApplicationManager.getInstance()
    }

    override fun tearDown() {
        super.tearDown()
    }

    private suspend fun syncProjectToServer(testDataName: String, port: Int, branchRef: BranchReference) {
        val project = openTestProject(testDataName)
        val service = IModelSyncService.getInstance(project)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef)
        binding.flush()
        binding.close()
        project.close()
    }

    fun `test initial sync to server`(): Unit = runWithModelServer { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference()
        syncProjectToServer("initial", port, branchRef)

        val client = ModelClientV2.builder().url("http://localhost:$port").build()
        val version = client.pull(branchRef, null)
        val rootNode = TreePointer(version.getTree()).getRootNode()
        val allNodes = rootNode.getDescendants(true)
        assertEquals(183, allNodes.count())
    }

    fun `test checkout into empty project`(): Unit = runWithModelServer { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference()
        syncProjectToServer("initial", port, branchRef)

        val emptyProject = openTestProject(null)
        val service = IModelSyncService.getInstance(emptyProject)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef)
        binding.flush()

        readAction {
            assertEquals(4, mpsProject.projectModules.size)

            val allNodes = mpsProject.projectModules.asSequence()
                .map { MPSModuleAsNode(it) }
                .flatMap { it.getDescendants(true) }
            assertEquals(177, allNodes.count())
        }
    }

    fun `test write to new repo after checkout`(): Unit = runWithModelServer { port ->
        val branchRef1 = RepositoryId("sync-test-A").getBranchReference()
        val branchRef2 = RepositoryId("sync-test-B").getBranchReference()
        syncProjectToServer("initial", port, branchRef1)

        val emptyProject = openTestProject(null)
        val service = IModelSyncService.getInstance(emptyProject)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef1)
        binding.flush()

        readAction {
            assertEquals(4, mpsProject.projectModules.size)

            val allNodes = mpsProject.projectModules.asSequence()
                .map { MPSModuleAsNode(it) }
                .flatMap { it.getDescendants(true) }
            assertEquals(177, allNodes.count())
        }

        binding.close()

        val binding2 = connection.bind(branchRef2)
        binding2.flush()

        suspend fun pullJson(ref: BranchReference) = connection
            .pullVersion(ref)
            .getTree()
            .let { TreePointer(it) }
            .getRootNode()
            .asData()
            .normalizeIds()
            .toJson()

        assertEquals(pullJson(branchRef1), pullJson(branchRef2))
    }

    fun `test sync after MPS change`(): Unit = runWithModelServer { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference()
        openTestProject("initial")
        val service = IModelSyncService.getInstance(mpsProject)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef)
        val version1 = binding.flush()

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

        val changes: List<TreeChangeEvent> = version2.getTree().asAsyncTree().getChanges(version1.getTree().asAsyncTree(), false).toList().getSuspending()
        assertEquals(1, changes.size)
        val change = changes.single() as PropertyChangedEvent
        assertEquals(MPSProperty(nameProperty).getUID(), change.role.getUID())
        assertEquals("MyClass", version1.getTree().getProperty(change.nodeId, change.role.key(version1.getTree())))
        assertEquals("Changed", version2.getTree().getProperty(change.nodeId, change.role.key(version1.getTree())))
    }

    fun `test sync after model-server change`(): Unit = runWithModelServer { port ->
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

        val client = ModelClientV2.builder().url("http://localhost:$port").build().also { it.init() }
        client.runWriteOnBranch(branchRef) { branch ->
            val node = branch.getRootNode().getDescendants(true)
                .first { it.getPropertyValue(nameProperty) == "MyClass" }
            node.setPropertyValue(nameProperty, "Changed")
        }
        val version2 = binding.flush()

        assertEquals("Changed", readAction { mpsNode.getProperty(nameProperty.property) })
    }

    fun `test new node on model-server`(): Unit = runWithModelServer { port ->
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

        readAction {
            val siblings = mpsNode.model!!.rootNodes
            val newNode = siblings.first { it.getProperty(nameProperty.property) == "NewClass" }
            assertEquals("NewClass", newNode.getProperty(nameProperty.property))
            assertEquals(newNodeIdOnServer, newNode.nodeId.tryDecodeModelixReference()?.serialize())
        }
    }

    fun `test sync after reconnect`(): Unit = runWithModelServer { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference()
        syncProjectToServer("initial", port, branchRef)
        syncProjectToServer("change1", port, branchRef)
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

    private fun NodeData.normalizeIds() = normalizeIds(HashMap(), AtomicLong())

    private fun NodeData.normalizeIds(idMap: MutableMap<String, String>, idGenerator: AtomicLong): NodeData {
        fun replaceId(id: String) = idMap.getOrPut(id) { "normalized:" + idGenerator.incrementAndGet() }

        return copy(
            id = id?.let { replaceId(it) },
            children = children.map { it.normalizeIds(idMap, idGenerator) },
            references = references.mapValues { replaceId(it.value) },
        )
    }
}
