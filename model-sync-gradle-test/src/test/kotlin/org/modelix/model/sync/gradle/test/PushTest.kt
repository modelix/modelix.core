package org.modelix.model.sync.gradle.test

import GraphLang.L_GraphLang
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.modelix.model.ModelFacade
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2PlatformSpecificBuilder
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.data.ModelData
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.RepositoryLanguage
import org.modelix.model.server.Main
import org.modelix.model.sync.asExported
import java.io.File
import kotlin.test.assertEquals

class PushTest {

    @Test
    fun `nodes were synced to server`() {
        val inputJson = File("build/model-sync/testPush").listFiles()
            ?.first { it.exists() && it.extension == "json" } ?: throw RuntimeException("input json not found")

        val inputRoot = ModelData.fromJson(inputJson.readText()).root

        ILanguageRepository.default.registerLanguage(L_GraphLang)
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
            assertEquals(inputRoot, branch.getRootNode().asExported())
        }

        // TODO Prepare next stage
    }
}
