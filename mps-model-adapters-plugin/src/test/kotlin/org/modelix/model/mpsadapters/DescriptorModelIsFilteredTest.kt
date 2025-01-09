package org.modelix.model.mpsadapters

import org.modelix.model.api.BuiltinLanguages

class DescriptorModelIsFilteredTest : MpsAdaptersTestBase("SimpleProject") {

    fun `test descriptor model is filtered by adapter`() {
        readAction {
            val module = checkNotNull(mpsProject.projectModules.find { it.moduleName == "Solution1" })

            val descriptorModels = module.models.filter { it.name.stereotype == "descriptor" }
            if (descriptorModels.isEmpty()) return@readAction // they don't seem to exist in MPS 2024.1 anymore

            assertEquals(1, descriptorModels.size)
            assertEquals(2, module.models.count())

            assertEquals(1, MPSModuleAsNode(module).asLegacyNode().getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).count())
        }
    }
}
