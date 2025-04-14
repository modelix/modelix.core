package org.modelix.mps.gitimport

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

private val modelServerDir = Path.of("../model-server").absolute().normalize()
private val modelServerImage = ImageFromDockerfile()
    .withDockerfile(modelServerDir.resolve("Dockerfile"))

fun runWithModelServer(body: suspend (port: Int) -> Unit) = runBlocking {
    @OptIn(ExperimentalTime::class)
    withTimeout(5.minutes) {
        val modelServer: GenericContainer<*> = GenericContainer(modelServerImage)
            .withExposedPorts(28101)
            .withCommand("-inmemory")
            .withEnv("MODELIX_VALIDATE_VERSIONS", "true")
//            .withEnv("MODELIX_REJECT_EXISTING_OBJECT", "true")
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
