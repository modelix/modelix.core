package org.modelix.mps.sync3

import jetbrains.mps.smodel.SNodeUtil
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.modelix.datastructures.model.MutationParameters
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.getName
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWriteOnTree
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSProjectReference
import org.modelix.model.mpsadapters.toModelix
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.asModelSingleThreaded
import org.modelix.model.mutable.getRootNode
import org.modelix.model.mutable.setProperty
import org.modelix.mps.api.ModelixMpsApi
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
    private var branchRefLib = RepositoryId("lib-repository").getBranchReference()
    private val service: IModelSyncService get() = IModelSyncService.getInstance(mpsProject)

    fun `test checkout`() = runTest { port, client ->
        val expectedLibHash = client.pullHash(branchRefLib)
        openProjectWithBindings(port)

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
                "lib.module3" to false,
                "lib.module4" to false,
            ),
            readAction { mpsProject.projectModules.map { it.moduleName to it.isReadOnly }.toSet() },
        )

        // lib repository is read only and should remain unchanged
        assertEquals(expectedLibHash, client.pullHash(branchRefLib))
    }

    fun `test checkout with non-existing library branch`() = runTest { port, client ->
        branchRefLib = branchRefLib.repositoryId.getBranchReference("non-existing-branch")
        openProjectWithBindings(port)
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
                "lib.module3" to false,
                "lib.module4" to false,
            ),
            readAction { mpsProject.projectModules.map { it.moduleName to it.isReadOnly }.toSet() },
        )
    }

    fun `test checkout with non-existing library repository`() = runTest { port, client ->
        branchRefLib = RepositoryId("non-existing-repository").getBranchReference("non-existing-branch")
        openProjectWithBindings(port)
        service.getBindings().forEach { it.flush() }

        val modulesInMpsRepo = readAction { mpsProject.repository.modules.map { it.moduleName }.toSet() }
        assertContainsElements(
            modulesInMpsRepo,
            "main.module1",
            "main.module2",
        )
        assertDoesntContain(
            modulesInMpsRepo,
            "lib.module3",
            "lib.module4",
        )
        assertEquals(
            setOf(
                "main.module1" to false,
                "main.module2" to false,
            ),
            readAction { mpsProject.projectModules.map { it.moduleName to it.isReadOnly }.toSet() },
        )
    }

    fun `test can get binding for module`() = runTest { port, client ->
        openProjectWithBindings(port)

        assertEquals(2, service.getBindings().size)
        service.getBindings().forEach { it.flush() }

        assertEquals(
            setOf(
                "main.module1" to false,
                "main.module2" to false,
                "lib.module3" to true,
                "lib.module4" to true,
            ),
            readAction {
                mpsProject.projectModules.map {
                    it.moduleName to IModelSyncService.getInstance(mpsProject).getBinding(it)?.isReadonly()
                }.toSet()
            },
        )
    }

    fun `test add module in main repository`() = runTest { port, client ->
        openProjectWithBindings(port)

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
                "lib.module3" to false,
                "lib.module4" to false,
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
                "lib.module3" to false,
                "lib.module4" to false,
            ),
            readAction { mpsProject.projectModules.map { it.moduleName to it.isReadOnly }.toSet() },
        )
    }

    fun `test add module in lib repository`() = runTest { port, client ->
        openProjectWithBindings(port)

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
                "lib.module3" to false,
                "lib.module4" to false,
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
                "lib.module3" to false,
                "lib.module4" to false,
                "lib.newModule" to false,
            ),
            readAction { mpsProject.projectModules.map { it.moduleName to it.isReadOnly }.toSet() },
        )
    }

    fun `test add root node in MPS`() = runTest { port, client ->
        openProjectWithBindings(port)

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

    fun `test add root node to library model in MPS`() = runTest { port, client ->
        openProjectWithBindings(port)

        val service = IModelSyncService.getInstance(mpsProject)
        assertEquals(2, service.getBindings().size)
        service.getBindings().forEach { it.flush() }

        // add new root node
        val classConcept = MetaAdapterFactory.getConcept(-0xcf9e5ac6dd9b33bL, -0x5bbc06ad3150a7eaL, 0xf8c108ca66L, "jetbrains.mps.baseLanguage.structure.ClassConcept")
        writeAction {
            val module = mpsProject.projectModules.first { it.moduleName == "lib.module3" }
            val model = module.models.first { it.name.simpleName == "modelA" }
            model.addRootNode(
                model.createNode(classConcept).also {
                    it.setProperty(SNodeUtil.property_INamedConcept_name, "MyClass")
                },
            )
        }

        service.getBindings().forEach { it.flush() }

        val version = client.pull(branchRefLib, null)
        val repositoryNode = version.getModelTree().asModelSingleThreaded().getRootNode()
        val module1 = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference())
            .first { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()) == "lib.module3" }
        val modelA = module1.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models.toReference())
            .first { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()) == "lib.module3.modelA" }
        val classNode = modelA.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.toReference()).firstOrNull()
        val className = classNode?.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())
        assertNull("ClassConcept $className found, but shouldn't exist", classNode)
    }

    fun `test reopen project after local library changes`() = runTest { port, client ->
        openProjectWithBindings(port)

        assertEquals(2, service.getBindings().size)
        service.getBindings().forEach { it.flush() }

        // add new root node
        val classConcept = MetaAdapterFactory.getConcept(-0xcf9e5ac6dd9b33bL, -0x5bbc06ad3150a7eaL, 0xf8c108ca66L, "jetbrains.mps.baseLanguage.structure.ClassConcept")
        writeAction {
            val module = mpsProject.projectModules.first { it.moduleName == "lib.module3" }
            val model = module.models.first { it.name.simpleName == "modelA" }
            model.addRootNode(
                model.createNode(classConcept).also {
                    it.setProperty(SNodeUtil.property_INamedConcept_name, "MyClass")
                },
            )
        }

        service.getBindings().forEach { it.flush() }

        // check that the root node still exists
        readAction {
            val module = mpsProject.projectModules.first { it.moduleName == "lib.module3" }
            val model = module.models.first { it.name.simpleName == "modelA" }
            assertEquals(1, model.rootNodes.count())
            assertEquals("MyClass", model.rootNodes.first().name)
        }

        assertEquals(1, ModelixMpsApi.getMPSProjects().size)
        assertEquals(1, com.intellij.openapi.project.ProjectManager.getInstance().openProjects.size)
        project.close()
        assertEquals(0, ModelixMpsApi.getMPSProjects().size)
        assertEquals(0, com.intellij.openapi.project.ProjectManager.getInstance().openProjects.size)

        openProjectWithBindings(port)
        assertEquals(1, ModelixMpsApi.getMPSProjects().size)
        assertEquals(1, com.intellij.openapi.project.ProjectManager.getInstance().openProjects.size)
        service.getBindings().forEach { it.flush() }

        // root node should be reverted (removed)
        readAction {
            val module = mpsProject.projectModules.first { it.moduleName == "lib.module3" }
            val model = module.models.first { it.name.simpleName == "modelA" }
            assertEquals(0, model.rootNodes.count())
        }
    }

    fun `test switch lib branch`() = runTest { port, client ->
        // create new branch from master
        val branchRefLib2 = branchRefLib.repositoryId.getBranchReference("second-branch")
        client.pull(branchRefLib, null).let {
            client.push(branchRefLib2, it, it)
        }
        // make some changes so that it's different from the master branch
        client.runWriteOnTree(branchRefLib2, nodeIdGenerator = { MPSIdGenerator(client.getIdGenerator(), it) }) { tree ->
            val repo = tree.getRootNode()
            val model = repo.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference())
                .single { it.getName() == "lib.module3" }
                .getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models.toReference())
                .single { it.getName() == "lib.module3.modelA" }
            val classConcept = MetaAdapterFactory.getConcept(-0xcf9e5ac6dd9b33bL, -0x5bbc06ad3150a7eaL, 0xf8c108ca66L, "jetbrains.mps.baseLanguage.structure.ClassConcept").toModelix()
            model
                .addNewChild(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.toReference(), 0, classConcept.getReference())
                .setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference(), "MyNewClass")
        }

        openProjectWithBindings(port)

        assertEquals(2, service.getBindings().size)
        service.getBindings().forEach { it.flush() }

        readAction {
            assertEquals(
                null,
                mpsProject.projectModules.single { it.moduleName == "lib.module3" }.models.single { it.name.longName == "lib.module3.modelA" }.rootNodes.firstOrNull()?.name,
            )
        }

        IModelSyncService.getInstance(mpsProject).switchBranch(branchRefLib, branchRefLib2, dropLocalChanges = true)

        service.getBindings().forEach { it.flush() }

        readAction {
            assertEquals(
                "MyNewClass",
                mpsProject.projectModules.single { it.moduleName == "lib.module3" }.models.single { it.name.longName == "lib.module3.modelA" }.rootNodes.single().name,
            )
        }
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
        val client = ModelClientV2.builder().url("http://localhost:$port").lazyAndBlockingQueries().build().also { it.init() }
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
