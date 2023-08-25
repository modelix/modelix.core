package org.modelix.model.sync.gradle.test

import GraphLang.L_GraphLang
import GraphLang.N_Node
import GraphLang._C_UntypedImpl_Node
import jetbrains.mps.lang.core.L_jetbrains_mps_lang_core
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.modelix.metamodel.TypedLanguagesRegistry
import org.modelix.metamodel.typed
import org.modelix.model.ModelFacade
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IBranch
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2PlatformSpecificBuilder
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.RepositoryLanguage
import org.modelix.model.server.Main
import org.modelix.model.sync.asExported
import java.io.File
import kotlin.test.assertEquals

class PushTest {

    @Test
    fun `nodes were synced to server`() {
        val inputDir = File("build/model-sync/testPush")
        val files = inputDir.listFiles()?.filter { it.extension == "json" } ?: error("no json files found")

        val modules = files.map { ModelData.fromJson(it.readText()) }
        val inputModel = ModelData(root = NodeData(children = modules.map { it.root }))

        TypedLanguagesRegistry.register(L_GraphLang)
        TypedLanguagesRegistry.register(L_jetbrains_mps_lang_core)
        ILanguageRepository.default.registerLanguage(RepositoryLanguage)

        val repoId = RepositoryId("ci-test")
        val branchName = "master"
        val url = "http://0.0.0.0:${Main.DEFAULT_PORT}/v2"
        val branchRef = ModelFacade.createBranchReference(repoId, branchName)
        val client = ModelClientV2PlatformSpecificBuilder().url(url).build()

        val branch = runBlocking {
            client.init()
            client.getReplicatedModel(branchRef).start()
        }
        branch.runRead {
            branch.getRootNode().allChildren.forEachIndexed { index, child ->
                assertEquals(inputModel.root.children[index], child.asExported())
            }
        }

        applyChangesForPullTest(branch)
    }

    private fun applyChangesForPullTest(branch: IBranch) {
        branch.runWrite {
            val graphNodes = branch.getRootNode()
                .getDescendants(false)
                .filter { it.getConceptReference() == ConceptReference(_C_UntypedImpl_Node.getUID()) }
                .map { it.typed<N_Node>() }
                .toList()

            graphNodes[0].name = "X"
            graphNodes[1].name = "Y"
            graphNodes[2].name = "Z"
        }
    }
}
