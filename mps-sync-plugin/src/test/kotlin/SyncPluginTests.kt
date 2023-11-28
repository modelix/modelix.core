/*
 * Copyright (c) 2023.
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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import junit.framework.TestCase
import org.modelix.authorization.installAuthentication
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.mps.sync.ModelSyncService

@OptIn(UnstableModelixFeature::class)
class SyncPluginTests : BasePlatformTestCase() {

    private fun runTestWithModelServer(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            install(ContentNegotiation) {
                json()
            }
            install(io.ktor.server.websocket.WebSockets)
            ModelReplicationServer(InMemoryStoreClient()).init(this)
        }
        block()
    }

    fun testEstablishConnection() = runTestWithModelServer {
        val syncService = ApplicationManager.getApplication().getService(ModelSyncService::class.java)
        syncService.ensureStarted()
        syncService.connectModelServerSuspending(client, "http://localhost/v2/", null)
        val modelClient = syncService.syncService.getAllClients().single()
        TestCase.assertNotSame(0, modelClient.getClientId())
    }

    fun testMPSProjectLoaded() {
        println(ApplicationManager::class.java.classLoader)
        val mpsProjects = jetbrains.mps.project.ProjectManager.getInstance().openedProjects
        TestCase.assertEquals(1, mpsProjects.size)
    }
}
