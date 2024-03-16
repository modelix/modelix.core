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
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.dependencies
import java.io.File
import java.util.zip.ZipInputStream

val Project.mpsMajorVersion: String get() {
    if (project != rootProject) return rootProject.mpsVersion
    return project.findProperty("mps.version.major")?.toString()?.takeIf { it.isNotEmpty() }
        ?: project.findProperty("mps.version")?.toString()?.takeIf { it.isNotEmpty() }?.replace(Regex("""(20\d\d\.\d+).*"""), "$1")
        ?: "2021.1"
}

val Project.mpsVersion: String get() {
    if (project != rootProject) return rootProject.mpsVersion
    return project.findProperty("mps.version")?.toString()?.takeIf { it.isNotEmpty() }
        ?: mpsMajorVersion.let {
            requireNotNull(
                mapOf(
                    // https://artifacts.itemis.cloud/service/rest/repository/browse/maven-mps/com/jetbrains/mps/
                    "2020.3" to "2020.3.6",
                    "2021.1" to "2021.1.4",
                    "2021.2" to "2021.2.6",
                    "2021.3" to "2021.3.5",
                    "2022.2" to "2022.2.2",
                    "2022.3" to "2022.3.1",
                    "2023.2" to "2023.2",
                    "2023.3" to "2023.3-RC1",
                )[it],
            ) { "Unknown MPS version: $it" }
        }
}

val Project.mpsPlatformVersion: Int get() {
    return mpsVersion.replace(Regex("""20(\d\d)\.(\d+).*"""), "$1$2").toInt()
}

val Project.mpsJavaVersion: Int get() = if (mpsPlatformVersion >= 223) 17 else 11

val Project.mpsHomeDir: Provider<Directory> get() {
    if (project != rootProject) return rootProject.mpsHomeDir
    return project.layout.buildDirectory.dir("mps-$mpsVersion")
}

fun Project.copyMps(): File {
    if (project != rootProject) return rootProject.copyMps()

    val mpsHome = mpsHomeDir.get().asFile
    if (mpsHome.exists()) return mpsHome

    println("Extracting MPS ...")

    // Extract MPS during configuration phase, because using it in intellij.localPath requires it to already exist.
    val mpsZip = configurations.create("mpsZip")
    dependencies {
        mpsZip("com.jetbrains:mps:$mpsVersion")
    }
    sync {
        from(zipTree({ mpsZip.singleFile }))
        into(mpsHomeDir)
    }

    // The IntelliJ gradle plugin doesn't search in jar files when reading plugin descriptors, but the IDE does.
    // Copy the XML files from the jars to the META-INF folders to fix that.
    for (pluginFolder in (mpsHomeDir.get().asFile.resolve("plugins").listFiles() ?: emptyArray())) {
        val jars = (pluginFolder.resolve("lib").listFiles() ?: emptyArray()).filter { it.extension == "jar" }
        for (jar in jars) {
            jar.inputStream().use {
                ZipInputStream(it).use { zip ->
                    val entries = generateSequence { zip.nextEntry }
                    for (entry in entries) {
                        if (entry.name.substringBefore("/") != "META-INF") continue
                        val outputFile = pluginFolder.resolve(entry.name)
                        if (outputFile.extension != "xml") continue
                        if (outputFile.exists()) {
                            println("already exists: $outputFile")
                            continue
                        }
                        outputFile.parentFile.mkdirs()
                        outputFile.writeBytes(zip.readAllBytes())
                        println("copied $outputFile")
                    }
                }
            }
        }
    }

    // The build number of a local IDE is expected to contain a product code, otherwise an exception is thrown.
    val buildTxt = mpsHomeDir.get().asFile.resolve("build.txt")
    val buildNumber = buildTxt.readText()
    val prefix = "MPS-"
    if (!buildNumber.startsWith(prefix)) {
        buildTxt.writeText("$prefix$buildNumber")
    }

    println("Extracting MPS done.")
    return mpsHome
}
