package org.modelix.model.client2

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.modelix.model.oauth.IAuthRequestHandler
import org.modelix.model.oauth.ModelixAuthClient
import org.modelix.model.oauth.OAuthConfig
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ModelixAuthClientTest {

    @Test
    fun `authorization can be canceled`() = runBlocking {
        val client = ModelixAuthClient()

        var browseCalled = false

        assertFailsWith<CancellationException> {
            withTimeout(500.milliseconds) {
                // The implementation of authorize calls java.util.concurrent.Semaphore.acquireUninterruptibly
                // which blocks the thread forever is the callback URL is never called.
                // The ModelixAuthClient launches an additional coroutine that catches the cancellation and invokes
                // Semaphore.release to unblock the thread.
                client.authorize(
                    OAuthConfig(
                        tokenUrl = "http://localhost/token",
                        authorizationUrl = "http://localhost/auth",
                        authRequestHandler = object : IAuthRequestHandler {
                            override fun browse(url: String) {
                                browseCalled = true
                            }
                        },
                    ),
                )
            }
        }

        assertTrue(browseCalled)
    }
}
