package org.modelix.model.server.handlers

import io.kotest.assertions.ktor.client.shouldHaveContentType
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.modelix.model.server.MODELIX_VERSION
import org.modelix.model.server.installDefaultServerPlugins

class AboutApiTest {

    private val aboutV1ContentType = ContentType.parse("application/x.modelix.about+json;version=1")
        .withCharset(Charsets.UTF_8)

    private fun ApplicationTestBuilder.setupApplication(): HttpClient {
        application {
            installDefaultServerPlugins()
            routing {
                AboutApiImpl.installRoutes(this)
            }
        }
        return createClient()
    }

    private fun ApplicationTestBuilder.createClient() =
        createClient {
            install(ContentNegotiation) {
                json()
                register(aboutV1ContentType, KotlinxSerializationConverter(DefaultJson))
            }
        }

    @Test
    fun getAboutInformationWithVersionedContentTypeV1() = testApplication {
        val client = setupApplication()

        val response = client.get("/about") {
            accept(aboutV1ContentType)
        }

        response shouldHaveStatus HttpStatusCode.OK
        response.body<AboutV1>() shouldBe AboutV1(MODELIX_VERSION)
        response.shouldHaveContentType(aboutV1ContentType)
    }

    @Test
    fun getAboutInformationWithJsonContentType() = testApplication {
        val client = setupApplication()

        val response = client.get("/about")

        response shouldHaveStatus HttpStatusCode.OK
        response.body<AboutV1>() shouldBe AboutV1(MODELIX_VERSION)
        response.shouldHaveContentType(ContentType.Application.Json.withCharset(Charsets.UTF_8))
    }
}
