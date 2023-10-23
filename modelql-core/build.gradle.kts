/*
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
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
}

kotlin {
    jvm()
    js(IR) {
        browser {}
        nodejs {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }
        useCommonJs()
    }

    @Suppress("UNUSED_VARIABLE", "KotlinRedundantDiagnosticSuppress")
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":kotlin-utils"))
                implementation(kotlin("stdlib-common"))
                implementation(libs.kotlin.reflect)
                implementation(libs.kotlin.logging)
                implementation(libs.kotlin.serialization.json)
                api(libs.kotlin.coroutines.core)
            }
            kotlin.srcDir(project.layout.buildDirectory.dir("version_gen"))
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.coroutines.test)
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
            }
        }
        val jvmTest by getting {
            dependencies {
            }
        }
        val jsMain by getting {
            dependencies {
            }
        }
        val jsTest by getting {
            dependencies {
            }
        }
    }
}

val generateVersionVariable by tasks.creating {
    doLast {
        val outputDir = project.layout.buildDirectory.dir("version_gen/org/modelix/modelql/core").get().asFile
        outputDir.mkdirs()
        outputDir.resolve("Version.kt").writeText(
            """
            package org.modelix.modelql.core

            const val modelqlVersion: String = "$version"

            """.trimIndent(),
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    dependsOn(generateVersionVariable)
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xjvm-default=all-compatibility")
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompileCommon>().all {
    dependsOn(generateVersionVariable)
    kotlinOptions {
    }
}
