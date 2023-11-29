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
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.HeavyPlatformTestCase
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import jetbrains.mps.ide.project.ProjectHelper
import org.modelix.authorization.installAuthentication
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.mps.sync.ModelSyncService
import java.io.File
import java.nio.file.Path

@OptIn(UnstableModelixFeature::class)
abstract class SyncPluginTestBase(private val testDataName: String?) : HeavyPlatformTestCase() {
    protected fun runTestWithModelServer(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
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

    protected fun runTestWithSyncService(body: suspend (ModelSyncService) -> Unit) = runTestWithModelServer {
        val syncService = ApplicationManager.getApplication().getService(ModelSyncService::class.java)
        try {
            syncService.ensureStarted()
            syncService.connectModelServerSuspending(client, "http://localhost/v2/", null)
            body(syncService)
        } finally {
            Disposer.dispose(syncService)
        }
    }

    override fun isCreateDirectoryBasedProject(): Boolean = true

    override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path {
        val projectDir = super.getProjectDirOrFile(isDirectoryBasedProject)
        val testSpecificDataName = testDataName
            ?: getTestName(false).substringAfterLast("_with_", "").takeIf { it.isNotEmpty() }
        if (testSpecificDataName != null) {
            val sourceDir = File("testdata/$testSpecificDataName")
            sourceDir.copyRecursively(projectDir.toFile(), overwrite = true)
        }
        return projectDir
    }

    protected fun getMPSProject(): org.jetbrains.mps.openapi.project.Project {
        return checkNotNull(ProjectHelper.fromIdeaProject(project)) { "MPS project not loaded" }
    }
}
