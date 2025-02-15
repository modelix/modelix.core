package org.modelix.mps.sync3

import com.intellij.testFramework.TestApplicationManager
import jetbrains.mps.ide.project.ProjectHelper
import kotlinx.coroutines.runBlocking
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Path
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
        val binding = connection.bind(branchRef).use {
            it.flush()
        }
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
        val service = IModelSyncService.getInstance(project)
        val connection = service.addServer("http://localhost:$port")
        connection.bind(branchRef)
        
        val rootNode = MPSRepositoryAsNode(ProjectHelper.fromIdeaProject(emptyProject)!!.repository)
        val allNodes = rootNode.getDescendants(true)
        assertEquals(183, allNodes.count())
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

}