import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.modelix.registerVersionGenerationTask

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
    `modelix-kotlin-multiplatform`
    kotlin("plugin.serialization")
    `maven-publish`
}

kotlin {
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xjvm-default=all-compatibility")
    }
}

project.registerVersionGenerationTask("org.modelix.modelql.core")
