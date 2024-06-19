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

package org.modelix.model.sync.bulk.lib.test
import kotlinx.serialization.json.Json
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.data.ModelData
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.sync.bulk.ExistingAndExpectedNode
import org.modelix.model.sync.bulk.asExported
import org.modelix.mps.model.sync.bulk.MPSBulkSynchronizer
import org.xmlunit.builder.Input
import org.xmlunit.matchers.EvaluateXPathMatcher.hasXPath

class ResolveInfoUpdateTest : MPSTestBase() {

    fun `test resolve info is updated with name from INamedConcept (testdata ResolveInfoUpdateTest)`() {
        val exportedModuleJson = exportModuleJson()
        val modifiedModuleJson = exportedModuleJson.replace("referencedNodeA", "referencedNodeANewName")
        val modifiedModule: ModelData = Json.decodeFromString(modifiedModuleJson)

        val getModulesToImport = { sequenceOf(ExistingAndExpectedNode(getTestModule(), modifiedModule)) }
        MPSBulkSynchronizer.importModelsIntoRepository(mpsProject.repository, getTestModule(), false, getModulesToImport)

        assertReferenceHasResolveInfo("3vHUMVfa0RY", "referencedNodeANewName")
    }

    fun `test resolve info is updated with resolveInfo from IResolveInfo (testdata ResolveInfoUpdateTest)`() {
        val exportedModuleJson = exportModuleJson()
        val modifiedModuleJson = exportedModuleJson.replace("referencedNodeC", "referencedNodeCNewName")
        val modifiedModule: ModelData = Json.decodeFromString(modifiedModuleJson)

        val getModulesToImport = { sequenceOf(ExistingAndExpectedNode(getTestModule(), modifiedModule)) }
        MPSBulkSynchronizer.importModelsIntoRepository(mpsProject.repository, getTestModule(), false, getModulesToImport)

        assertReferenceHasResolveInfo("3vHUMVfa0RZ", "referencedNodeCNewName")
    }

    private fun assertReferenceHasResolveInfo(referencedNode: String, expectedResolveInfo: String) {
        val testModelPath = projectDir.resolve("solutions/NewSolution/models/NewSolution.a_model.mps")
        val testModelXml = Input.fromPath(testModelPath).build()
        assertThat(testModelXml, hasXPath("model/node/node/ref[@node='$referencedNode']/@resolve", equalTo(expectedResolveInfo)))
    }

    private fun getTestModule(): INode {
        var result: INode? = null
        mpsProject.repository.modelAccess.runReadAction {
            val repository = mpsProject.repository
            val repositoryNode = MPSRepositoryAsNode(repository)
            result = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "NewSolution" }
        }
        return checkNotNull(result)
    }

    private fun exportModuleJson(): String {
        var result: String? = null
        mpsProject.repository.modelAccess.runReadAction {
            val module = getTestModule()
            val modelData = ModelData(root = module.asExported())
            result = modelData.toJson()
        }
        return checkNotNull(result)
    }
}
