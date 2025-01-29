package org.modelix.model.sync.bulk.lib.test
import kotlinx.serialization.json.Json
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.data.ModelData
import org.modelix.model.mpsadapters.asLegacyNode
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
            val repositoryNode = repository.asLegacyNode()
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
