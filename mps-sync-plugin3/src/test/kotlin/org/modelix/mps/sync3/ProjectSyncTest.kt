package org.modelix.mps.sync3

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.modelix.model.client2.ModelClientV2
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class ProjectSyncTest {

    @Test
    fun test1() = runWithModelServer { port ->
        val client = ModelClientV2.builder().url("http://localhost:$port").build()
        println(client.getServerId())
    }

    private fun runWithModelServer(body: suspend (port: Int) -> Unit) = runTest(timeout = 3.minutes) {
        val modelServerDir = Path.of("../model-server").absolute().normalize()
        val image = ImageFromDockerfile()
            .withDockerfile(modelServerDir.resolve("Dockerfile"))
            .withFileFromPath("/", modelServerDir)
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