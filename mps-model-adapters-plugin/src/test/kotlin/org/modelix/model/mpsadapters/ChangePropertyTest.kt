package org.modelix.model.mpsadapters

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode

class ChangePropertyTest : MpsAdaptersTestBase("SimpleProject") {

    fun testModuleCreation() {
        readAction {
            assertEquals(1, mpsProject.projectModules.size)
        }

        val repositoryNode: INode = mpsProject.repository.asLegacyNode()

        runCommandOnEDT {
            val module = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1" }
            val model = module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1.model1" }
            val rootNode = model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).single()
            assertEquals("Class1", rootNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name))
            rootNode.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, "MyRenamedClass")
            assertEquals("MyRenamedClass", rootNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name))
        }
    }
}
