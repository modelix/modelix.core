/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.sync.bulk.gradle.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class ExportFromMps @Inject constructor(of: ObjectFactory) : JavaExec() {

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val mpsHome: DirectoryProperty = of.directoryProperty()

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    val antScript: RegularFileProperty = of.fileProperty()

    @OutputDirectory
    val jsonDir: DirectoryProperty = of.directoryProperty()

    @TaskAction
    override fun exec() {
        val jsonDir = jsonDir.get().asFile
        val mpsHome = mpsHome.get().asFile
        val antVariables = listOf(
            "mps.home" to mpsHome.absolutePath,
            "mps_home" to mpsHome.absolutePath,
            "build.dir" to jsonDir.absolutePath,
        ).map { (key, value) -> "-D$key=$value" }

        args("-v")
        args(antVariables)
        args("-buildfile", antScript.get())
        args("export-modules")
        super.exec()
    }
}
