package org.modelix.mps.sync3

import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IWritableNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWriteOnTree
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSProjectReference
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.getRootNode
import org.modelix.model.mutable.setProperty
import org.modelix.mps.multiplatform.model.MPSIdGenerator
import org.modelix.mps.multiplatform.model.MPSModuleReference
import org.modelix.mps.multiplatform.model.MPSProjectModuleReference
import kotlin.io.path.writeText

/**
 * This covers the use case where, in addition to the primary repository, a read-only library is loaded from a
 * second repository.
 * The library repository is expected to also contain a project, but its ID and name isn't relevant and doesn't have
 * to match the local project name.
 */
class LibraryRepositoryTest : ProjectSyncTestBase() {

    private fun IWritableNode.addNewModule(name: String) = addNewChild(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference(), -1, BuiltinLanguages.MPSRepositoryConcepts.Solution.getReference()).also {
        it.setPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference(), MPSModuleReference.tryConvert(it.getNodeReference())!!.moduleId)
        it.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference(), name)
    }

    fun `test checkout`() = runWithModelServer { port ->
        val client = ModelClientV2.builder().url("http://localhost:$port").lazyAndBlockingQueries().build()
        val branchRefMain = RepositoryId("main-repository").getBranchReference()
        val branchRefLib = RepositoryId("lib-repository").getBranchReference()
        client.initRepository(branchRefMain.repositoryId)
        client.initRepository(branchRefLib.repositoryId)
        client.runWriteOnTree(branchRefMain, nodeIdGenerator = { MPSIdGenerator(client.getIdGenerator(), it) }) { tree ->
            val repo = tree.getRootNode()
            repo.changeConcept(BuiltinLanguages.MPSRepositoryConcepts.Repository.getReference())
            createProjectAndModules(tree, "main-project", listOf("main.module1", "main.module2"))
        }
        client.runWriteOnTree(branchRefLib, nodeIdGenerator = { MPSIdGenerator(client.getIdGenerator(), it) }) { tree ->
            val repo = tree.getRootNode()
            repo.changeConcept(BuiltinLanguages.MPSRepositoryConcepts.Repository.getReference())
            createProjectAndModules(tree, "lib-project", listOf("lib.module3", "lib.module4"))
        }
        val expectedLibHash = client.pullHash(branchRefLib)

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
                      <readonly>true</readonly>
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
            "main.module1",
            "main.module2",
            "lib.module3",
            "lib.module4",
        )
        assertEquals(
            setOf(
                "main.module1" to false,
                "main.module2" to false,
                "lib.module3" to true,
                "lib.module4" to true,
            ),
            readAction { mpsProject.projectModules.map { it.moduleName to it.isReadOnly }.toSet() },
        )

        // lib repository is read only and should remain unchanged
        assertEquals(expectedLibHash, client.pullHash(branchRefLib))
    }

    fun createProjectAndModules(tree: IMutableModelTree, projectName: String, moduleNames: List<String>) {
        val repo = tree.getRootNode()
        repo.changeConcept(BuiltinLanguages.MPSRepositoryConcepts.Repository.getReference())

        val modules = moduleNames.map { repo.addNewModule(it) }

        val t = tree.getWriteTransaction()
        t.mutate(
            MutationParameters.AddNew(
                t.tree.getRootNodeId(),
                BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference(),
                -1,
                listOf(MPSProjectReference(projectName) to BuiltinLanguages.MPSRepositoryConcepts.Project.getReference()),
            ),
        )
        t.setProperty(MPSProjectReference(projectName), BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference(), projectName)

        t.mutate(
            MutationParameters.AddNew(
                MPSProjectReference(projectName),
                BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference(),
                -1,
                modules.map {
                    MPSProjectModuleReference(MPSModuleReference.convert(it.getNodeReference()), MPSProjectReference(projectName)) to BuiltinLanguages.MPSRepositoryConcepts.ProjectModule.getReference()
                },
            ),
        )
        for (module in modules) {
            t.mutate(
                MutationParameters.Reference(
                    MPSProjectModuleReference(MPSModuleReference.convert(module.getNodeReference()), MPSProjectReference(projectName)),
                    BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference(),
                    module.getNodeReference(),
                ),
            )
        }
    }
}
