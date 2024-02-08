package org.modelix.model.sync.bulk.gradle.test

import GraphLang.C_Graph
import GraphLang._C_UntypedImpl_Graph
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.modelix.model.ModelFacade
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IBranch
import org.modelix.model.api.IProperty
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2PlatformSpecificBuilder
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.sync.bulk.asExported
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PushTest {
    private val url = "http://0.0.0.0:28309/v2"
    private val branchRef = ModelFacade.createBranchReference(RepositoryId("ci-test"), "master")
    private val client = ModelClientV2PlatformSpecificBuilder().url(url).build().apply { runBlocking { init() } }

    private fun runTest(body: (IBranch) -> Unit) {
        val replicatedModel = client.getReplicatedModel(branchRef)
        val branch = runBlocking { replicatedModel.start() }
        body(branch)
        replicatedModel.dispose()
    }

    @Test
    fun `nodes were synced to server`() = runTest { branch ->
        val inputDir = File("build/model-sync/testPush")
        val files = inputDir.listFiles()?.filter { it.extension == "json" } ?: error("no json files found in ${inputDir.absolutePath}")

        val modules = files.map { ModelData.fromJson(it.readText()) }
        val inputModel = ModelData(root = NodeData(children = modules.map { it.root }))

        branch.runRead {
            assertContentEquals(inputModel.root.children, branch.getRootNode().allChildren.map { it.asExported() })
        }
    }

    @Test
    fun `meta properties were applied to root node`() = runTest { branch ->
        branch.runRead {
            val actual1 = branch.getRootNode().getPropertyValue(IProperty.fromName("metaKey1"))
            val actual2 = branch.getRootNode().getPropertyValue(IProperty.fromName("metaKey2"))

            assertEquals("metaValue1", actual1)
            assertEquals("metaValue2", actual2)
        }
    }

    @Test
    fun `cross module references were synced`() = runTest { branch ->
        branch.runRead {
            val rootNode = branch.getRootNode()
            val solution1Graph = checkNotNull(
                rootNode.allChildren
                    .find { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "GraphSolution" }
                    ?.getDescendants(false)
                    ?.find { it.getConceptReference() == ConceptReference(_C_UntypedImpl_Graph.getUID()) },
            )

            val solution2Graph = checkNotNull(
                rootNode.allChildren
                    .find { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "GraphSolution2" }
                    ?.getDescendants(false)
                    ?.find { it.getConceptReference() == ConceptReference(_C_UntypedImpl_Graph.getUID()) },
            )
            assertEquals(solution1Graph, solution2Graph.getReferenceTarget(C_Graph.relatedGraph))
        }
    }
}
