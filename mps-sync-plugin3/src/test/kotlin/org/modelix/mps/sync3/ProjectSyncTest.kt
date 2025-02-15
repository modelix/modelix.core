package org.modelix.mps.sync3

import com.intellij.testFramework.TestApplicationManager
import kotlinx.coroutines.runBlocking
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.absolute
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class ProjectSyncTest : MPSTestBase() {

    private val modelServerDir = Path.of("../model-server").absolute().normalize()
    private val modelServerImage = ImageFromDockerfile()
        .withDockerfile(modelServerDir.resolve("Dockerfile"))

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
        syncProjectToServer("nonTrivialProject", port, branchRef)

        val client = ModelClientV2.builder().url("http://localhost:$port").build()
        val version = client.pull(branchRef, null)
        val rootNode = TreePointer(version.getTree()).getRootNode()
        val allNodes = rootNode.getDescendants(true)
        assertEquals(183, allNodes.count())
    }

    fun `test checkout into empty project`(): Unit = runWithModelServer { port ->
        val branchRef = RepositoryId("sync-test").getBranchReference()
        syncProjectToServer("nonTrivialProject", port, branchRef)

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
        val branchRef1 = RepositoryId("sync-test-c1").getBranchReference()
        val branchRef2 = RepositoryId("sync-test2-c2").getBranchReference()
        syncProjectToServer("nonTrivialProject", port, branchRef1)

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

        suspend fun pullJson(ref: BranchReference) = connection.pullVersion(ref).getTree().let { TreePointer(it) }.getRootNode().asData().normalizeIds().toJson()

        assertEquals(pullJson(branchRef1), pullJson(branchRef2))
    }

    private fun runWithModelServer(body: suspend (port: Int) -> Unit) = runBlocking {
        val mps: GenericContainer<*> = GenericContainer(modelServerImage)
            .withExposedPorts(28101)
            .withCommand("-inmemory")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(3.minutes.toJavaDuration()))
            .withLogConsumer {
                println(it.utf8StringWithoutLineEnding)
            }

        mps.start()
        try {
            body(mps.firstMappedPort)
        } finally {
            mps.stop()
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
