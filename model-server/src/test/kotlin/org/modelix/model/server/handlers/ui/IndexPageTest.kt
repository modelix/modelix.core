package org.modelix.model.server.handlers.ui

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.modelix.model.client.successful
import org.modelix.model.server.installDefaultServerPlugins
import kotlin.test.Test
import kotlin.test.assertTrue

class IndexPageTest {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installDefaultServerPlugins()
            IndexPage().init(this)
        }

        block()
    }

    @Test
    fun `index page is reachable`() = runTest {
        val response = client.get("/")

        assertTrue { response.successful }
        assertTrue { response.bodyAsText().contains("Model Server") }
    }
}
