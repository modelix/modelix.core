package org.modelix.mps.sync3

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

fun runWithModelServer(hmacKey: String? = null, body: suspend (port: Int) -> Unit) = runBlocking {
    @OptIn(ExperimentalTime::class)
    withTimeout(5.minutes) {
        val modelServer: GenericContainer<*> = GenericContainer(System.getProperty("modelix.model.server.image"))
            .withExposedPorts(28101)
            .withCommand("-inmemory")
            .withEnv("MODELIX_VALIDATE_VERSIONS", "true")
//            .withEnv("MODELIX_REJECT_EXISTING_OBJECT", "true")
            .also {
                if (hmacKey != null) {
                    it.withEnv("MODELIX_PERMISSION_CHECKS_ENABLED", "true")
                    it.withEnv("MODELIX_JWT_SIGNATURE_HMAC512_KEY", hmacKey)
                }
            }
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
