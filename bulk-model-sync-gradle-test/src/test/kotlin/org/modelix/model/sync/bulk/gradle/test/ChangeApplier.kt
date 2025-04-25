package org.modelix.model.sync.bulk.gradle.test

import GraphLang.C_Node
import GraphLang.L_GraphLang
import GraphLang.N_Edge
import GraphLang.N_Graph
import GraphLang.N_Node
import GraphLang._C_UntypedImpl_Edge
import GraphLang._C_UntypedImpl_Graph
import GraphLang._C_UntypedImpl_Node
import jetbrains.mps.lang.core.L_jetbrains_mps_lang_core
import kotlinx.coroutines.runBlocking
import org.modelix.metamodel.TypedLanguagesRegistry
import org.modelix.metamodel.typed
import org.modelix.metamodel.untyped
import org.modelix.model.ModelFacade
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.getDescendants
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWrite
import org.modelix.model.lazy.RepositoryId
import kotlin.test.Test

/**
 * Not an actual test. Just a preparation for [PullTest].
 * Marking it as a test makes it easily callable from the ci script.
 */
class ChangeApplier {

    @Test
    fun applyChangesForPullTest() {
        val url = "http://0.0.0.0:28309/v2"
        val branchRef = ModelFacade.createBranchReference(RepositoryId("ci-test"), "master")
        val client = ModelClientV2.builder().lazyAndBlockingQueries().url(url).build().apply { runBlocking { init() } }

        TypedLanguagesRegistry.register(L_GraphLang)
        TypedLanguagesRegistry.register(L_jetbrains_mps_lang_core)

        runBlocking {
            client.runWrite(branchRef) { rootNode ->
                val graphNodes = rootNode
                    .getDescendants(false)
                    .filter { it.getConceptReference() == ConceptReference(_C_UntypedImpl_Node.getUID()) }
                    .map { it.typed<N_Node>() }
                    .toList()

                graphNodes[0].name = "X"
                graphNodes[1].name = "Y"
                graphNodes[2].name = "Z"

                val graph = graphNodes[0].untyped().parent?.typed<N_Graph>() ?: error("parent not found")
                graph.nodes.addNew(3, C_Node).apply {
                    name = "NewNode"
                }

                val edges = rootNode
                    .getDescendants(false)
                    .filter { it.getConceptReference() == ConceptReference(_C_UntypedImpl_Edge.getUID()) }
                    .map { it.typed<N_Edge>() }
                    .toList()

                edges[0].source = graphNodes[1]
                edges[0].target = graphNodes[3]

                val solution1Graph = rootNode.allChildren
                    .find { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "GraphSolution" }
                    ?.getDescendants(false)
                    ?.find { it.getConceptReference() == ConceptReference(_C_UntypedImpl_Graph.getUID()) }
                    ?.typed<N_Graph>()

                val solution2Graph = rootNode.allChildren
                    .find { it.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name) == "GraphSolution2" }
                    ?.getDescendants(false)
                    ?.find { it.getConceptReference() == ConceptReference(_C_UntypedImpl_Graph.getUID()) }
                    ?.typed<N_Graph>()

                solution1Graph?.relatedGraph = solution2Graph
            }
        }
    }
}
