package org.modelix.mps.sync3

import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IWritableNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWriteOnModel
import org.modelix.model.client2.runWriteOnTree
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSProjectModuleReference
import org.modelix.model.mpsadapters.MPSProjectReference
import org.modelix.model.mutable.getRootNode
import org.modelix.model.mutable.setProperty
import org.modelix.mps.multiplatform.model.MPSIdGenerator
import org.modelix.mps.multiplatform.model.MPSModuleReference
import kotlin.io.path.writeText

class MultipleBindingsTest : ProjectSyncTestBase() {

    private fun IWritableNode.addNewModule(name: String) = addNewChild(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference(), -1, BuiltinLanguages.MPSRepositoryConcepts.Solution.getReference()).also {
        it.setPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference(), MPSModuleReference.tryConvert(it.getNodeReference())!!.moduleId)
        it.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference(), name)
    }

    fun `test no overlap`() = runWithModelServer { port ->
        val client = ModelClientV2.builder().url("http://localhost:$port").lazyAndBlockingQueries().build()
        val branchRefMain = RepositoryId("main-repository").getBranchReference()
        val branchRefLib = RepositoryId("lib-repository").getBranchReference()
        client.initRepository(branchRefMain.repositoryId)
        client.initRepository(branchRefLib.repositoryId)
        client.runWriteOnTree(branchRefMain, nodeIdGenerator = { MPSIdGenerator(client.getIdGenerator(), it) }) { tree ->
            val repo = tree.getRootNode()
            repo.changeConcept(BuiltinLanguages.MPSRepositoryConcepts.Repository.getReference())

            val module1 = repo.addNewModule("module1")
            val module2 = repo.addNewModule("module2")

            val t = tree.getWriteTransaction()
            t.mutate(
                MutationParameters.AddNew(
                    t.tree.getRootNodeId(),
                    BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference(),
                    -1,
                    listOf(MPSProjectReference("test-project") to BuiltinLanguages.MPSRepositoryConcepts.Project.getReference()),
                ),
            )
            t.setProperty(MPSProjectReference("test-project"), BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference(), "test-project")

            t.mutate(
                MutationParameters.AddNew(
                    MPSProjectReference("test-project"),
                    BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference(),
                    -1,
                    listOf(
                        MPSProjectModuleReference(MPSModuleReference.convert(module1.getNodeReference()), MPSProjectReference("test-project")) to BuiltinLanguages.MPSRepositoryConcepts.ProjectModule.getReference(),
                        MPSProjectModuleReference(MPSModuleReference.convert(module2.getNodeReference()), MPSProjectReference("test-project")) to BuiltinLanguages.MPSRepositoryConcepts.ProjectModule.getReference(),
                    ),
                ),
            )
            t.mutate(
                MutationParameters.Reference(
                    MPSProjectModuleReference(MPSModuleReference.convert(module1.getNodeReference()), MPSProjectReference("test-project")),
                    BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference(),
                    module1.getNodeReference(),
                ),
            )
            t.mutate(
                MutationParameters.Reference(
                    MPSProjectModuleReference(MPSModuleReference.convert(module2.getNodeReference()), MPSProjectReference("test-project")),
                    BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference(),
                    module2.getNodeReference(),
                ),
            )
        }
        client.runWriteOnModel(branchRefLib, nodeIdGenerator = { MPSIdGenerator(client.getIdGenerator(), it) }) { repo ->
            repo.changeConcept(BuiltinLanguages.MPSRepositoryConcepts.Repository.getReference())
            val module3 = repo.addNewModule("module3")
            val module4 = repo.addNewModule("module4")
        }

        openTestProject(null, projectName = "test-project") { projectDir ->
            projectDir.resolve(".mps").resolve("modelix.xml").writeText(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                  <component name="modelix-sync">
                    <binding>
                      <enabled>true</enabled>
                      <url>http://localhost:$port</url>
                      <repository>${branchRefMain.repositoryId.id}</repository>
                      <branch>${branchRefMain.branchName}</branch>
                    </binding>
                    <binding>
                      <enabled>true</enabled>
                      <url>http://localhost:$port</url>
                      <repository>${branchRefLib.repositoryId.id}</repository>
                      <branch>${branchRefLib.branchName}</branch>
                    </binding>
                  </component>
                </project>
                """.trimIndent(),
            )
        }

        val service = IModelSyncService.getInstance(mpsProject)
        assertEquals(2, service.getBindings().size)
        service.getBindings().forEach { it.flush() }

        assertContainsElements(
            readAction { mpsProject.repository.modules.map { it.moduleName }.toSet() },
            "module1",
            "module2",
            "module3",
            "module4",
        )
        assertEquals(
            setOf("module1", "module2", "module3", "module4"),
            readAction { mpsProject.projectModules.map { it.moduleName }.toSet() },
        )
    }
}
