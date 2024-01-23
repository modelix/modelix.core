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
    alias(libs.plugins.kotlin.jvm)
    id("org.modelix.bulk-model-sync")
}

val modelixCoreVersion: String = file("../version.txt").readText()

version = modelixCoreVersion

repositories {
    val modelixRegex = "org\\.modelix.*"
    mavenLocal {
        content {
            includeGroupByRegex(modelixRegex)
        }
    }
    gradlePluginPortal {
        content {
            excludeGroupByRegex(modelixRegex)
        }
    }
    maven {
        url = uri("https://artifacts.itemis.cloud/repository/maven-mps/")
        content {
            includeGroupByRegex(modelixRegex)
            includeGroup("com.jetbrains")
        }
    }
    mavenCentral {
        content {
            excludeGroupByRegex(modelixRegex)
        }
    }
}

val kotlinGenDir = project.layout.buildDirectory.dir("metamodel/kotlin").get().asFile.apply { mkdirs() }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
    implementation("org.modelix:model-server:$modelixCoreVersion")
    implementation("org.modelix:model-api-gen-runtime:$modelixCoreVersion")
    testImplementation("org.modelix:model-client:$modelixCoreVersion")
    testImplementation("org.modelix:bulk-model-sync-lib:$modelixCoreVersion")
    testImplementation("org.modelix.mps:model-adapters:$modelixCoreVersion")
    testImplementation("org.modelix:graph-lang-api:$modelixCoreVersion")
    testImplementation(kotlin("test"))
    testImplementation(libs.xmlunit.core)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

tasks.register("runModelServer", JavaExec::class) {
    group = "modelix"

    description = "Launches a model-server instance to be used as a target for the test. " +
        "This task will block and is intended to be run in a separate process, apart from the actual test execution."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.modelix.model.server.Main")
    args("-inmemory", "-port", "28309")
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
        includeModule("GraphSolution")
        fromLocal {
            mpsHeapSize = "2g"
            repositoryDir = repoDir
        }
        toModelServer {
            url = "http://localhost:28309/v2"
            repositoryId = "ci-test"
            branchName = "master"
        }
    }
    direction("testPull") {
        includeModule("GraphSolution")
        fromModelServer {
            url = "http://localhost:28309/v2"
            repositoryId = "ci-test"
            branchName = "master"
        }
        toLocal {
            repositoryDir = repoDir
        }
    }
}
