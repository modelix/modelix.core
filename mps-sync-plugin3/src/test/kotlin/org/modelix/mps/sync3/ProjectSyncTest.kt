package org.modelix.mps.sync3

import com.intellij.testFramework.TestApplicationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.RepositoryId
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class ProjectSyncTest : MPSTestBase() {
    override fun setUp() {
        super.setUp()
        TestApplicationManager.getInstance()
    }

    override fun tearDown() {
        super.tearDown()
    }

    fun `test initial sync to server`(): Unit = runWithModelServer { port ->
        val project = openTestProject("nonTrivialProject")
        val service = IModelSyncService.getInstance(project)
        val connection = service.addServer("http://localhost:$port")
        val branchRef = RepositoryId("sync-test").getBranchReference()
        val binding = connection.bind(branchRef)
        binding.flush()
        println("sync done")
        delay(5.seconds)

        val client = ModelClientV2.builder().url("http://localhost:$port").build()
        val version = client.pull(branchRef, null)
        val numNodes = TreePointer(version.getTree()).getRootNode().getDescendants(true).count()
        assertEquals(10, numNodes)
    }

    private fun runWithModelServer(body: suspend (port: Int) -> Unit) = runBlocking {
        val modelServerDir = Path.of("../model-server").absolute().normalize()
        println(modelServerDir)
        val image = ImageFromDockerfile()
            .withDockerfile(modelServerDir.resolve("Dockerfile"))
            //.withFileFromPath("build", modelServerDir.resolve("build"))
        val mps: GenericContainer<*> = GenericContainer(image)
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