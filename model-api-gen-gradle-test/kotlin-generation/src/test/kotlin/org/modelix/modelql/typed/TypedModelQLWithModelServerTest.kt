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

package org.modelix.modelql.typed

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestInstance
import org.modelix.apigen.test.ApigenTestLanguages
import org.modelix.model.ModelFacade
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWrite
import org.modelix.model.lazy.RepositoryId
import org.modelix.modelql.client.ModelQLClient
import kotlin.test.BeforeTest

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TypedModelQLWithModelServerTest : TypedModelQLTest() {
    private var testRun = 0
    private val modelClient = ModelClientV2.builder().url("http://localhost:28102/v2/").build().also { runBlocking { it.init() } }
    private val branchRef
        get() = ModelFacade.createBranchReference(RepositoryId("modelql-test$testRun"), "master")

    override fun runTest(block: suspend (ModelQLClient) -> Unit) {
        val modelQlClient = ModelQLClient.builder()
            .url("http://localhost:28102/v2/repositories/${branchRef.repositoryId.id}/branches/${branchRef.branchName}/query")
            .build()

        runBlocking { block(modelQlClient) }
    }

    init {
        ApigenTestLanguages.registerAll()
    }

    @BeforeTest
    fun setup() {
        testRun++
        runBlocking {
            modelClient.runWrite(branchRef) {
                createTestData(it)
            }
        }
    }
}
