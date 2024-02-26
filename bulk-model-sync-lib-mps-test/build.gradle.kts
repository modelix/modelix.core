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
    // We are not building an actual plugin here.
    // We use/abuse the gradle-intellij-plugin run tests with MPS.
    // (With enough time and effort,
    // one could inspect what the plugin does under the hood
    // and build something custom using the relevant parts.
    // For the time being, this solution works without much overhead and great benefit.)
    alias(libs.plugins.intellij)
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

dependencies {
    testImplementation("org.modelix:bulk-model-sync-lib:$modelixCoreVersion")
    testImplementation("org.modelix.mps:model-adapters:$modelixCoreVersion")
    testImplementation(libs.kotlin.serialization.json)
}

val mpsVersion = project.findProperty("mps.version")?.toString()?.takeIf { it.isNotEmpty() }
    ?: "2021.1.4"
println("Building for MPS version $mpsVersion")

// Extract MPS during configuration phase, because using it in intellij.localPath requires it to already exist.
val mpsHome = project.layout.buildDirectory.dir("mps-$mpsVersion")
val mpsZip: Configuration by configurations.creating
dependencies { mpsZip("com.jetbrains:mps:$mpsVersion") }
mpsHome.get().asFile.let { baseDir ->
    if (baseDir.exists()) return@let // the content of MPS zip is not expected to change

    println("Extracting MPS ...")
    sync {
        from(zipTree({ mpsZip.singleFile }))
        into(mpsHome)
    }

    // The build number of a local IDE is expected to contain a product code, otherwise an exception is thrown.
    val buildTxt = mpsHome.get().asFile.resolve("build.txt")
    val buildNumber = buildTxt.readText()
    val prefix = "MPS-"
    if (!buildNumber.startsWith(prefix)) {
        buildTxt.writeText("$prefix$buildNumber")
    }

    println("Extracting MPS done.")
}

intellij {
    localPath = mpsHome.map { it.asFile.absolutePath }
}
