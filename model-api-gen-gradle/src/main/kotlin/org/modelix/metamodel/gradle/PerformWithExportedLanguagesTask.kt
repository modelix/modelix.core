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

package org.modelix.metamodel.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.modelix.metamodel.generator.ReadonlyProcessedLanguageSet
import org.modelix.metamodel.generator.UnstableGeneratorFeature
import org.modelix.metamodel.generator.processLanguageData
import javax.inject.Inject

private typealias ActionWithExportedLanguages = (ReadonlyProcessedLanguageSet) -> Unit

/**
 * This task allows to execute custom build steps which work with the generated and processed languages
 * from the "exportMetaModelFromMps" task.
 *
 * The languages can be accessed through the [ReadonlyProcessedLanguageSet] structure.
 * Custom user actions have to be registered as [ActionWithExportedLanguages]] in [performWithExportedLanguages]
 */
@CacheableTask
@UnstableGeneratorFeature
abstract class PerformWithExportedLanguagesTask @Inject constructor(of: ObjectFactory) : DefaultTask() {
    @get:InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val exportedLanguagesDir: DirectoryProperty = of.directoryProperty()

    @get:Input
    val includedNamespaces: ListProperty<String> = of.listProperty(String::class.java)

    @get:Input
    val includedLanguages: ListProperty<String> = of.listProperty(String::class.java)

    @get:Input
    val includedConcepts: ListProperty<String> = of.listProperty(String::class.java)

    private val actions: MutableList<ActionWithExportedLanguages> = mutableListOf()

    @TaskAction
    fun run() {
        val languagesData = loadLanguageDataFromExportedLanguages(exportedLanguagesDir.get().asFile)

        val processedLanguages = processLanguageData(
            languagesData,
            this.includedNamespaces.get(),
            this.includedLanguages.get(),
            this.includedConcepts.get(),
        )

        for (action in actions) {
            action(processedLanguages)
        }
    }

    fun performWithExportedLanguages(action: ActionWithExportedLanguages) {
        actions.add(action)
    }
}
