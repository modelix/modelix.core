package org.modelix.mps.sync3

import jetbrains.mps.smodel.SNodeUtil
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IWritableNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWriteOnTree
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSProjectReference
import org.modelix.model.mpsadapters.toModelix
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.asModelSingleThreaded
import org.modelix.model.mutable.getRootNode
import org.modelix.model.mutable.setProperty
import org.modelix.mps.multiplatform.model.MPSIdGenerator
import org.modelix.mps.multiplatform.model.MPSModelReference
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

    private val branchRefMain = RepositoryId("main-repository").getBranchReference()
    private val branchRefLib = RepositoryId("lib-repository").getBranchReference()

    fun `test checkout`() = runTest { port, client ->
        val expectedLibHash = client.pullHash(branchRefLib)
        openProjectWithBindings(port)

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

    fun `test add module in main repository`() = runTest { port, client ->
        openProjectWithBindings(port)

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

        // add new module
        client.runWriteOnTree(branchRefMain, nodeIdGenerator = { MPSIdGenerator(client.getIdGenerator(), it) }) { tree ->
            val repo = tree.getRootNode()
            val t = tree.getWriteTransaction()
            val module = repo.addNewModule("main.newModule")
            t.mutate(
                MutationParameters.AddNew(
                    MPSProjectReference("main-project"),
                    BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference(),
                    -1,
                    listOf(MPSProjectModuleReference(MPSModuleReference.convert(module.getNodeReference()), MPSProjectReference("main-project")) to BuiltinLanguages.MPSRepositoryConcepts.ProjectModule.getReference()),
                ),
            )
            t.mutate(
                MutationParameters.Reference(
                    MPSProjectModuleReference(MPSModuleReference.convert(module.getNodeReference()), MPSProjectReference("main-project")),
                    BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference(),
                    module.getNodeReference(),
                ),
            )
        }

        service.getBindings().forEach { it.flush() }

        assertContainsElements(
            readAction { mpsProject.repository.modules.map { it.moduleName }.toSet() },
            "main.module1",
            "main.module2",
            "main.newModule",
            "lib.module3",
            "lib.module4",
        )
        assertEquals(
            setOf(
                "main.module1" to false,
                "main.module2" to false,
                "main.newModule" to false,
                "lib.module3" to true,
                "lib.module4" to true,
            ),
            readAction { mpsProject.projectModules.map { it.moduleName to it.isReadOnly }.toSet() },
        )
    }

    fun `test add module in lib repository`() = runTest { port, client ->
        openProjectWithBindings(port)

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

        // add new module
        client.runWriteOnTree(branchRefLib, nodeIdGenerator = { MPSIdGenerator(client.getIdGenerator(), it) }) { tree ->
            val repo = tree.getRootNode()
            val t = tree.getWriteTransaction()
            val module = repo.addNewModule("lib.newModule")
            t.mutate(
                MutationParameters.AddNew(
                    MPSProjectReference("lib-project"),
                    BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference(),
                    -1,
                    listOf(MPSProjectModuleReference(MPSModuleReference.convert(module.getNodeReference()), MPSProjectReference("lib-project")) to BuiltinLanguages.MPSRepositoryConcepts.ProjectModule.getReference()),
                ),
            )
            t.mutate(
                MutationParameters.Reference(
                    MPSProjectModuleReference(MPSModuleReference.convert(module.getNodeReference()), MPSProjectReference("lib-project")),
                    BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference(),
                    module.getNodeReference(),
                ),
            )
        }

        service.getBindings().forEach { it.flush() }

        assertContainsElements(
            readAction { mpsProject.repository.modules.map { it.moduleName }.toSet() },
            "main.module1",
            "main.module2",
            "lib.module3",
            "lib.module4",
            "lib.newModule",
        )
        assertEquals(
            setOf(
                "main.module1" to false,
                "main.module2" to false,
                "lib.module3" to true,
                "lib.module4" to true,
                "lib.newModule" to true,
            ),
            readAction { mpsProject.projectModules.map { it.moduleName to it.isReadOnly }.toSet() },
        )
    }

    fun `test add root node in MPS`() = runTest { port, client ->
        openProjectWithBindings(port)

        val service = IModelSyncService.getInstance(mpsProject)
        assertEquals(2, service.getBindings().size)
        service.getBindings().forEach { it.flush() }

        // add new root node
        val classConcept = MetaAdapterFactory.getConcept(-0xcf9e5ac6dd9b33bL, -0x5bbc06ad3150a7eaL, 0xf8c108ca66L, "jetbrains.mps.baseLanguage.structure.ClassConcept")
        writeAction {
            val model = mpsProject.projectModules.first { it.moduleName == "main.module1" }
                .models.first { it.name.simpleName == "modelA" }
            model.addRootNode(
                model.createNode(classConcept).also {
                    it.setProperty(SNodeUtil.property_INamedConcept_name, "MyClass")
                },
            )
        }

        service.getBindings().forEach { it.flush() }

        val version = client.pull(branchRefMain, null)
        val repositoryNode = version.getModelTree().asModelSingleThreaded().getRootNode()
        val module1 = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference())
            .first { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()) == "main.module1" }
        val modelA = module1.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models.toReference())
            .first { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()) == "main.module1.modelA" }
        val classNode = modelA.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.toReference()).first()
        assertEquals(classConcept.toModelix().getReference(), classNode.getConceptReference())
        assertEquals("MyClass", classNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()))
    }

    private fun IWritableNode.addNewModule(name: String, modelNames: List<String> = emptyList()): IWritableNode {
        return addNewChild(
            BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference(),
            -1,
            BuiltinLanguages.MPSRepositoryConcepts.Solution.getReference(),
        ).also { module ->
            module.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.Module.id.toReference(),
                MPSModuleReference.tryConvert(module.getNodeReference())!!.moduleId,
            )
            module.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference(), name)
            for (modelName in modelNames) {
                val model = module.addNewChild(
                    BuiltinLanguages.MPSRepositoryConcepts.Module.models.toReference(),
                    -1,
                    BuiltinLanguages.MPSRepositoryConcepts.Model.getReference(),
                )
                model.setPropertyValue(
                    BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference(),
                    modelName,
                )
                model.setPropertyValue(
                    BuiltinLanguages.MPSRepositoryConcepts.Model.id.toReference(),
                    MPSModelReference.convert(model.getNodeReference()).modelId,
                )
            }
        }
    }

    private fun runTest(body: suspend (port: Int, client: ModelClientV2) -> Unit) = runWithModelServer { port ->
        val client = ModelClientV2.builder().url("http://localhost:$port").lazyAndBlockingQueries().build()
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

        body(port, client)
    }

    fun createProjectAndModules(tree: IMutableModelTree, projectName: String, moduleNames: List<String>) {
        val repo = tree.getRootNode()
        repo.changeConcept(BuiltinLanguages.MPSRepositoryConcepts.Repository.getReference())

        val modules = moduleNames.map { repo.addNewModule(it, listOf("$it.modelA")) }

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

    private fun openProjectWithBindings(port: Int) {
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
    }
}
