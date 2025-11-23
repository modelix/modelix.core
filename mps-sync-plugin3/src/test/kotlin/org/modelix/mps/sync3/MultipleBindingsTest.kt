package org.modelix.mps.sync3

import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IWritableNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWriteOnModel
import org.modelix.model.client2.runWriteOnTree
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSProjectReference
import org.modelix.model.mutable.getRootNode
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
            val t = tree.getWriteTransaction()
            t.mutate(
                MutationParameters.AddNew(
                    t.tree.getRootNodeId(),
                    BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference(),
                    -1,
                    listOf(MPSProjectReference("test-project") to BuiltinLanguages.MPSRepositoryConcepts.Project.getReference()),
                ),
            )
            val module1 = repo.addNewModule("module1")
            val module2 = repo.addNewModule("module2")
        }
        client.runWriteOnModel(branchRefLib, nodeIdGenerator = { MPSIdGenerator(client.getIdGenerator(), it) }) { repo ->
            repo.changeConcept(BuiltinLanguages.MPSRepositoryConcepts.Repository.getReference())
            val module3 = repo.addNewModule("module3")
            val module4 = repo.addNewModule("module4")
        }

        openTestProject(null) { projectDir ->
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
