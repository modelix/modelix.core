/*
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

@file:OptIn(ExperimentalTime::class)

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.testing.testApplication
import jetbrains.mps.smodel.SNodeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jetbrains.mps.openapi.module.ModelAccess
import org.modelix.client.light.LightModelClient
import org.modelix.client.light.filterLoaded
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.addNewChild
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.server.api.buildModelQuery
import org.modelix.model.server.light.LightModelServer
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class LightModelClientTest : MpsTestBase("SimpleProject") {

    private fun runTest(block: suspend (HttpClient) -> Unit) = testApplication {
        application {
            LightModelServer.builder().rootNode { MPSRepositoryAsNode(mpsProject.repository) }.build().apply {
                installHandlers()
            }
        }
        val client = createClient {
            install(WebSockets)
        }
        block(client)
    }

    fun runClientTest(block: suspend (suspend (debugName: String) -> LightModelClient) -> Unit) = runTest { httpClient ->
        withTimeout(2.minutes) {
            val createClient: suspend (debugName: String) -> LightModelClient = { debugName ->
                val client = LightModelClient.builder()
                    .httpClient(httpClient)
                    .url("ws://localhost/ws")
                    .debugName(debugName)
                    .build()
                client.changeQuery(
                    buildModelQuery {
                        root {
                            children("modules") {
                                whereProperty("name").equalTo("Solution1")
                                descendants { }
                            }
                        }
                    },
                )
                wait { client.isInitialized() }
                wait { client.runRead { client.getRootNode()?.isValid == true } }
                client
            }
            block(createClient)
        }
    }

    fun `test property read`() = runClientTest { createClient ->
        val client = createClient("1")
        client.runRead {
            val repository = client.getRootNode()!!
            val modules = repository.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules).filterLoaded()
            val module = modules.single()
            val model = module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).filterLoaded().single()
            val cls = model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).single()
            val className = cls.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
            assertEquals("Class1", className)
        }
    }

    fun `test property write`() = runClientTest { createClient ->
        fun readClassNameFromMPS(): String? {
            val repository = mpsProject.repository
            return repository.modelAccess.computeRead {
                repository
                    .modules.single { it.moduleName == "Solution1" }
                    .models.single { it.name.longName == "Solution1.model1" }
                    .rootNodes.single()
                    .getProperty(SNodeUtil.property_INamedConcept_name)
            }
        }

        assertEquals("Class1", readClassNameFromMPS())

        val client = createClient("1")
        client.runWrite {
            val repository = client.getRootNode()!!
            val modules = repository.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules).filterLoaded()
            val module = modules.single()
            val model = module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).filterLoaded().single()
            val cls = model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).single()
            val className = cls.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
            assertEquals("Class1", className)
            cls.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, "RenamedClass")
        }

        wait { readClassNameFromMPS() != "Class1" }
        assertEquals("RenamedClass", readClassNameFromMPS())
    }

    fun `test add new child`() = runClientTest { createClient ->
        fun countChildrenInMPS(): Int {
            val repository = mpsProject.repository
            return repository.modelAccess.computeRead {
                repository
                    .modules.single { it.moduleName == "Solution1" }
                    .models.single { it.name.longName == "Solution1.model1" }
                    .rootNodes.single()
                    .children.count()
            }
        }

        assertEquals(2, countChildrenInMPS())

        val client = createClient("1")
        client.runWrite {
            val repository = client.getRootNode()!!
            val modules = repository.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules).filterLoaded()
            val module = modules.single()
            val model = module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).filterLoaded().single()
            val cls = model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).single()
            assertEquals(2, cls.allChildren.count())
            val constructorDeclarationConcept = ConceptReference("mps:f3061a53-9226-4cc5-a443-f952ceaf5816/1068580123140")
            val memberLink = IChildLink.fromUID("f3061a53-9226-4cc5-a443-f952ceaf5816/1107461130800/5375687026011219971")
            cls.addNewChild(memberLink, constructorDeclarationConcept)
            assertEquals(3, cls.allChildren.count())
        }

        wait { countChildrenInMPS() != 2 }
        assertEquals(3, countChildrenInMPS())
    }

    private suspend fun wait(condition: () -> Boolean) {
        withTimeout(30.seconds) {
            while (!condition()) {
                delay(1.milliseconds)
            }
        }
    }
}

fun <R> ModelAccess.computeRead(body: () -> R): R {
    var result: R? = null
    this.runReadAction {
        result = body()
    }
    @Suppress("UNCHECKED_CAST")
    return result as R
}
