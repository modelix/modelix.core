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

import GraphLang.L_GraphLang
import jetbrains.mps.lang.core.L_jetbrains_mps_lang_core
import org.modelix.model.server.Main

buildscript {
    val modelixCoreVersion: String = file("../version.txt").readText()
    dependencies {
        classpath("org.modelix:model-server:$modelixCoreVersion")
        classpath("org.modelix:graph-lang-api:$modelixCoreVersion")
    }
}

plugins {
    kotlin("jvm")
    id("org.modelix.bulk-model-sync")
}

val modelixCoreVersion: String = file("../version.txt").readText()

version = modelixCoreVersion

repositories {
    mavenLocal()
    maven { url = uri("https://repo.maven.apache.org/maven2") }
    maven { url = uri("https://plugins.gradle.org/m2/") }
    mavenCentral()
    maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
}

val mps by configurations.creating
val mpsDir = buildDir.resolve("mps").apply { mkdirs() }
val kotlinGenDir = buildDir.resolve("metamodel/kotlin").apply { mkdirs() }

dependencies {
    mps("com.jetbrains:mps:2021.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
    implementation("org.modelix:model-server:$modelixCoreVersion")
    implementation("org.modelix:model-api-gen-runtime:$modelixCoreVersion")
    testImplementation("org.modelix:model-client:$modelixCoreVersion")
    testImplementation("org.modelix:bulk-model-sync-lib:$modelixCoreVersion")
    testImplementation("org.modelix.mps:model-adapters:$modelixCoreVersion")
    testImplementation("org.modelix:graph-lang-api:$modelixCoreVersion")
    testImplementation(kotlin("test"))
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
    args("-inmemory")
    args("-useroleids")
}

val resolveMps by tasks.registering(Copy::class) {
    from(mps.resolve().map { zipTree(it) })
    into(mpsDir)
}

val repoDir = buildDir.resolve("test-repo")

val copyTestRepo by tasks.registering(Sync::class) {
    from(projectDir.resolve("test-repo"))
    into(repoDir)
}

modelSync {
    dependsOn(resolveMps)
    dependsOn(copyTestRepo)
    direction("testPush") {
        registerLanguage(L_GraphLang)
        registerLanguage(L_jetbrains_mps_lang_core)
        includeModule("GraphSolution")
        fromLocal {
            mpsHome = mpsDir
            mpsHeapSize = "2g"
            repositoryDir = repoDir
        }
        toModelServer {
            url = "http://0.0.0.0:${Main.DEFAULT_PORT}/v2"
            repositoryId = "ci-test"
            branchName = "master"
        }
    }
    direction("testPull") {
        fromModelServer {
            url = "http://0.0.0.0:${Main.DEFAULT_PORT}/v2"
            repositoryId = "ci-test"
            branchName = "master"
        }
        toLocal {
            mpsHome = mpsDir
            repositoryDir = repoDir
        }
    }
}
