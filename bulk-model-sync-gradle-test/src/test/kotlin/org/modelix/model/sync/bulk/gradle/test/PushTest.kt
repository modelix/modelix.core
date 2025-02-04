package org.modelix.model.sync.bulk.gradle.test

import GraphLang.C_Graph
import GraphLang._C_UntypedImpl_Graph
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.modelix.model.ModelFacade
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.PBranch
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2PlatformSpecificBuilder
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.sync.bulk.asExported
import org.modelix.model.withAutoTransactions
import java.io.File
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PushTest {
    private val url = "http://0.0.0.0:28309/v2"
    private val branchRef = ModelFacade.createBranchReference(RepositoryId("ci-test"), "master")
    private val client = ModelClientV2PlatformSpecificBuilder().url(url).build().apply { runBlocking { init() } }

    private fun runTest(body: (INode) -> Unit) {
        val baseVersion = runBlocking { client.pullIfExists(branchRef)!! }
        val branch = PBranch(baseVersion.getTree(), client.getIdGenerator()).withAutoTransactions()
        body(branch.getRootNode())
    }

    @Test
    fun `nodes were synced to server`() = runTest { root ->
        val inputDir = File("build/model-sync/testPush")
        val files = inputDir.listFiles()?.filter { it.extension == "json" } ?: error("no json files found in ${inputDir.absolutePath}")

        val modules = files.map { ModelData.fromJson(it.readText()) }
        val inputModel = ModelData(root = NodeData(children = modules.map { it.root }).normalize())

        assertEquals(inputModel.toJson(), ModelData(root = NodeData(children = root.allChildren.map { it.asExported() }).normalize()).toJson())
    }

    @Test
    fun `meta properties were applied to root node`() = runTest { root ->
        val actual1 = root.getPropertyValue(IProperty.fromName("metaKey1"))
        val actual2 = root.getPropertyValue(IProperty.fromName("metaKey2"))

        assertEquals("metaValue1", actual1)
        assertEquals("metaValue2", actual2)
    }

    @Test
    fun `cross module references were synced`() = runTest { root ->
        val solution1Graph = checkNotNull(
            root.allChildren
                .find { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "GraphSolution" }
                ?.getDescendants(false)
                ?.find { it.getConceptReference() == ConceptReference(_C_UntypedImpl_Graph.getUID()) },
        )

        val solution2Graph = checkNotNull(
            root.allChildren
                .find { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "GraphSolution2" }
                ?.getDescendants(false)
                ?.find { it.getConceptReference() == ConceptReference(_C_UntypedImpl_Graph.getUID()) },
        )
        assertEquals(solution1Graph, solution2Graph.getReferenceTarget(C_Graph.relatedGraph.untyped()))
    }
}
