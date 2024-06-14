/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.server.handlers

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.resources.Resources
import io.ktor.server.testing.testApplication
import org.modelix.api.operative.AboutInformation
import org.modelix.modelql.core.MODELIX_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals

class AboutInformationHandlerTest {
    @Test
    fun getAboutInformation() = testApplication {
        application {
            install(Resources)
            install(ContentNegotiation) {
                json()
            }
            AboutInformationHandler.init(this)
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() }
        }

        val response = client.get("/about")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(AboutInformation(MODELIX_VERSION), response.body<AboutInformation>())
    }
}
