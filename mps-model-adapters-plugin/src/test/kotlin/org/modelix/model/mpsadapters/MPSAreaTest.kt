package org.modelix.model.mpsadapters

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.mps.multiplatform.model.MPSModuleReference

class MPSAreaTest : MpsAdaptersTestBase("SimpleProject") {

    fun testResolveModuleInNonExistingProject() {
        val repositoryNode: INode = mpsProject.repository.asLegacyNode()
        val area = repositoryNode.getArea()
        readAction {
            val nonExistingProject = MPSProjectReference("nonExistingProject")
            val module = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules)
                .single { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "Solution1" }
            val projectModuleReference = MPSProjectModuleReference((module.reference as MPSModuleReference).toMPS(), nonExistingProject)

            val resolutionResult = area.resolveNode(projectModuleReference)

            assertNull(resolutionResult)
        }
    }
}
