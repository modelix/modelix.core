package org.modelix.model.sync.gradle.test

import org.junit.jupiter.api.Test
import org.modelix.model.data.ModelData
import java.io.File

class PullTest {

    @Test
    fun `nodes were synced to local`() {
        val inputJson = File("build/model-sync/testPull").listFiles()
            ?.first { it.exists() && it.extension == "json" } ?: throw RuntimeException("input json not found")

        val inputRoot = ModelData.fromJson(inputJson.readText()).root
    }
}
