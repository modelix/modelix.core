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

package org.modelix

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

/**
 * Causes code containing the Modelix version to be generated and used in a Kotlin project.
 * The Kotlin project can be a JVM or Multiplatform project.
 */
fun Project.registerVersionGenerationTask(packageName: String) {
    val packagePath = packageName.replace('.', '/')

    val generateVersionVariable = tasks.register("generateVersionVariable") {
        doLast {
            val fileContent = buildString {
                appendLine("package $packageName")
                appendLine()
                appendLine("""const val MODELIX_VERSION: String = "$version"""")
                if (project.name == "modelql-core") {
                    appendLine("""@Deprecated("Use the new MODELIX_VERSION", replaceWith = ReplaceWith("MODELIX_VERSION"))""")
                    appendLine("const val modelqlVersion: String = MODELIX_VERSION")
                }
            }
            val outputDir = layout.buildDirectory.dir("version_gen/$packagePath").get().asFile
            outputDir.mkdirs()
            outputDir.resolve("Version.kt").writeText(fileContent)
        }
    }

    // Generate version constant for Kotlin JVM projects
    plugins.withType<KotlinPluginWrapper> {
        extensions.configure<KotlinJvmProjectExtension> {
            sourceSets["main"].kotlin.srcDir(layout.buildDirectory.dir("version_gen"))
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
            dependsOn(generateVersionVariable)
        }
    }

    // Generate version constant for Kotlin Multiplatform projects
    plugins.withType<KotlinMultiplatformPluginWrapper> {
        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain {
                kotlin.srcDir(layout.buildDirectory.dir("version_gen"))
            }
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
            dependsOn(generateVersionVariable)
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon>().all {
            dependsOn(generateVersionVariable)
        }
    }
}
