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
package org.modelix.modelql.typed

import io.ktor.server.testing.testApplication
import org.modelix.apigen.test.ApigenTestLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBasedStore
import org.modelix.model.server.light.LightModelServer
import org.modelix.modelql.client.ModelQLClient
import kotlin.test.BeforeTest

class TypedModelQLTestWithLightModelServer : TypedModelQLTest() {
    private lateinit var branch: IBranch

    override fun runTest(block: suspend (ModelQLClient) -> Unit) = testApplication {
        application {
            LightModelServer(80, branch.getRootNode()).apply { installHandlers() }
        }
        val httpClient = createClient {
        }
        val modelQlClient = ModelQLClient.builder().url("http://localhost/query").httpClient(httpClient).build()
        block(modelQlClient)
    }

    @BeforeTest
    fun setup() {
        ApigenTestLanguages.registerAll()
        val tree = CLTree(null, null, ObjectStoreCache(MapBasedStore()), useRoleIds = true)
        branch = PBranch(tree, IdGenerator.getInstance(1))
        branch.runWrite {
            createTestData(branch.getRootNode())
        }
    }
}
