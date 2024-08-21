/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.sync.bulk.gradle

import io.kotest.matchers.file.shouldContainFile
import io.kotest.matchers.file.shouldContainNFiles
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test

class ExportFunctionalTest {

    private val corePath: String = System.getenv("MODELIX_CORE_PATH")

    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    private lateinit var projectDir: File
    private val gradle by lazy { GradleRunner.create().withProjectDir(projectDir) }

    @BeforeTest
    fun setup() {
        writeSettingsFile()
    }

    private fun writeSettingsFile() {
        // language=kotlin
        val settingsFileContent = """
            pluginManagement {
                includeBuild("$corePath")
                includeBuild("$corePath/build-logic")
            }
            plugins {
                id("modelix-repositories")
            }
            includeBuild("$corePath")

        """.trimIndent()

        projectDir.resolve("settings.gradle.kts").writeText(settingsFileContent)
    }

    private fun writeBuildScript(testCode: String) {
        // language=kotlin
        val commonBuildScript = """
            plugins {
                id("modelix-kotlin-jvm")
                id("org.modelix.bulk-model-sync")
            }
            mpsBuild {
                mpsVersion("2021.2.5")
            }
        """.trimIndent()

        projectDir.resolve("build.gradle.kts").writeText("$commonBuildScript\n\n$testCode")
    }

    @Test
    fun `module exclusion works`() {
        val expectedModule = "jetbrains.mps.baseLanguage.builders"

        // language=kotlin
        writeBuildScript(
            """
            modelSync {
                direction("push") {
                    val module = "jetbrains.mps.baseLanguage.classifiers"
                    val prefix = "jetbrains.mps.baseLanguage.regexp"
                    includeModule("$expectedModule")
                    includeModule(module)
                    excludeModule(module)
                    includeModulesByPrefix(prefix)
                    excludeModulesByPrefix(prefix)
                    fromLocal {
                        repositoryDir = File("${projectDir.path}")
                    }
                    toModelServer {
                        url = "localhost"
                        repositoryId = "abc"
                        branchName = "master"
                    }
                }
            }
            """.trimIndent(),
        )
        gradle.withArguments("pushExportFromMPS").build()

        val jsonDir = gradle.projectDir.resolve("build/model-sync/push")
        jsonDir shouldContainNFiles 1
        jsonDir shouldContainFile "$expectedModule.json"
    }
}
