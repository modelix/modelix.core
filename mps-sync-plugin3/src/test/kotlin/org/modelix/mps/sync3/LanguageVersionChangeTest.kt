package org.modelix.mps.sync3

import com.intellij.openapi.application.ApplicationInfo
import jetbrains.mps.project.Solution
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mutable.asReadOnlyModel

class LanguageVersionChangeTest : ProjectSyncTestBase() {

    private fun detectMpsVersion(): Int {
        val info = ApplicationInfo.getInstance()
        return (info.majorVersion.toInt() - 2000) * 10 + info.minorVersionMainPart.toInt()
    }

    fun `test language version change`(): Unit = runWithModelServer { port ->
        // When this test is executed on it makes other tests fail.
        // No idea why. Giving up for now.
        if (detectMpsVersion() < 241) return@runWithModelServer

        val branchRef = RepositoryId("language-version-test").getBranchReference()
        openTestProject("initial")
        val service = IModelSyncService.getInstance(mpsProject)
        val connection = service.addServer("http://localhost:$port")
        val binding = connection.bind(branchRef)
        val version1 = binding.flush()

        readAction {
            val solution = mpsProject.projectModules.first { it.moduleName == "NewSolution" }
            val bl = solution.usedLanguages.first { it.qualifiedName == "jetbrains.mps.baseLanguage" }
            assertEquals(11, solution.getUsedLanguageVersion(bl))
        }

        version1.getModelTree().asReadOnlyModel().let { model ->
            val modules = model.getRootNode().getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference())
            val solution = modules.first { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()) == "NewSolution" }
            val languageDependencies = solution.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.languageDependencies.toReference())
            val bl = languageDependencies.first { it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name.toReference()) == "jetbrains.mps.baseLanguage" }
            val langVersion = bl.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version.toReference())
            assertEquals("11", langVersion)
        }

        writeAction {
            val solution = mpsProject.projectModules.first { it.moduleName == "NewSolution" } as Solution
            val bl = solution.usedLanguages.first { it.qualifiedName == "jetbrains.mps.baseLanguage" }

            // https://github.com/JetBrains/MPS/blob/51dec876eaa578ea51a438e2f247595bbbf272fc/core/kernel/kernelSolution/source_gen/jetbrains/mps/smodel/ModuleDependencyVersions.java#L184-L194
            solution.moduleDescriptor.languageVersions[bl] = 0
            solution.setChanged()

            assertEquals(0, solution.getUsedLanguageVersion(bl))
        }

        val version2 = binding.flush()

        version2.getModelTree().asReadOnlyModel().let { model ->
            val modules = model.getRootNode().getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference())
            val solution = modules.first { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()) == "NewSolution" }
            val languageDependencies = solution.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.languageDependencies.toReference())
            val bl = languageDependencies.first { it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name.toReference()) == "jetbrains.mps.baseLanguage" }
            val langVersion = bl.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version.toReference())
            assertEquals("0", langVersion)
        }
    }
}
