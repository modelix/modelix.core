package org.modelix.model.sync.bulk.gradle.test

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.modelix.model.ModelFacade
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2PlatformSpecificBuilder
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.sync.bulk.asExported
import java.io.File
import kotlin.test.assertContentEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PushTest {
    private val url = "http://0.0.0.0:28309/v2"
    private val branchRef = ModelFacade.createBranchReference(RepositoryId("ci-test"), "master")
    private val client = ModelClientV2PlatformSpecificBuilder().url(url).build().apply { runBlocking { init() } }

    @Test
    fun `nodes were synced to server`() {
        val inputDir = File("build/model-sync/testPush")
        val files = inputDir.listFiles()?.filter { it.extension == "json" } ?: error("no json files found in ${inputDir.absolutePath}")

        val modules = files.map { ModelData.fromJson(it.readText()) }
        val inputModel = ModelData(root = NodeData(children = modules.map { it.root }))

        val replicatedModel = client.getReplicatedModel(branchRef)
        val branch = runBlocking { replicatedModel.start() }

        branch.runRead {
            assertContentEquals(inputModel.root.children, branch.getRootNode().allChildren.map { it.asExported() })
        }
        replicatedModel.dispose()
    }
}
