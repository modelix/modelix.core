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
import org.gradle.kotlin.dsl.support.unzipTo
import org.gradle.kotlin.dsl.support.zipTo
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
                    "2022.2" to "2022.2.3",
                    "2022.3" to "2022.3.1",
                    "2023.2" to "2023.2",
                    "2023.3" to "2023.3",
                    "2024.1" to "2024.1-EAP1",
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

    // Workaround for https://youtrack.jetbrains.com/issue/KT-69541/Kotlin-2.0-compiler-cannot-use-JAR-packaged-as-ZIP64
    //
    // The issue was first detected with `lib/app.jar` in 2022.2 and 2022.3.
    // This JAR is needed since this versions because it contained `com.intellij.openapi.project.ProjectManager`.
    // Before that, `lib/platform-api.jar` contained `com.intellij.openapi.project.ProjectManager`.
    //
    // The workaround is to unzip the JAR with and zip it again.
    // Unzipping with Gradle supports ZIP64, but ZIP64 is not used when zipping again.
    //
    // TODO MODELIX-968 Remove this workaround after it is not needed anymore.
    val appJarFile = mpsHomeDir.get().asFile.resolve("lib/app.jar")
    if (appJarFile.exists()) {
        val appJarContent = mpsHomeDir.get().asFile.resolve("lib/appJarContent")
        val appJarFileNotZip64 = mpsHomeDir.get().asFile.resolve("lib/appNotZip64.jar")
        println("Unzipping $appJarFile into $appJarContent")
        unzipTo(appJarContent, appJarFile)
        println("Zipping $appJarContent into $appJarFileNotZip64")
        zipTo(appJarFileNotZip64, appJarContent)
        println("Deleting $appJarContent")
        delete {
            this.delete(appJarContent)
        }
        println("Overriding $appJarFile with $appJarFileNotZip64")
        Files.move(appJarFileNotZip64.toPath(), appJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
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
