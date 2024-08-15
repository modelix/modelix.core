/*
 * Copyright (c) 2023.
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

plugins {
    `modelix-kotlin-jvm-with-junit-platform`
    id("org.modelix.bulk-model-sync")
}

val kotlinGenDir = project.layout.buildDirectory.dir("metamodel/kotlin").get().asFile.apply { mkdirs() }

dependencies {
    implementation(libs.kotlin.coroutines.core)
    implementation("org.modelix:model-server")
    implementation("org.modelix:model-api-gen-runtime")
    testImplementation(project(":graph-lang-api"))
    testImplementation("org.modelix", "model-client", "", "jvmRuntimeElements")
    testImplementation("org.modelix:bulk-model-sync-lib")
    testImplementation(kotlin("test"))
    testImplementation(libs.xmlunit.core)
}

val repoDir = project.layout.buildDirectory.dir("test-repo").get().asFile

val copyTestRepo by tasks.registering(Sync::class) {
    from(projectDir.resolve("test-repo"))
    into(repoDir)
}

mpsBuild {
    mpsVersion("2021.2.5")
}

modelSync {
    dependsOn(copyTestRepo)
    direction("testPush") {
        includeModulesByPrefix("GraphSolution")
        fromLocal {
            mpsHeapSize = "4g"
            repositoryDir = repoDir
        }
        toModelServer {
            url = "http://localhost:28309/v2"
            repositoryId = "ci-test"
            branchName = "master"
            metaProperties["metaKey1"] = "metaValue1"
            metaProperties["metaKey2"] = "metaValue2"
        }
    }
    direction("testPull") {
        includeModulesByPrefix("GraphSolution")
        fromModelServer {
            url = "http://localhost:28309/v2"
            repositoryId = "ci-test"
            branchName = "master"
        }
        toLocal {
            repositoryDir = repoDir
            mpsHeapSize = "4g"
        }
    }
}
