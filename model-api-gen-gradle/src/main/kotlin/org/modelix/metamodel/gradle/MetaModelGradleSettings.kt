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
package org.modelix.metamodel.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.modelix.metamodel.generator.NameConfig
import java.io.File

open class MetaModelGradleSettings {
    private var javaExecutableProvider: (() -> File)? = null
    var javaExecutable: File?
        get() = javaExecutableProvider?.invoke()
        set(value) {
            javaExecutableProvider = value?.let { { it } }
        }
    val moduleFolders = ArrayList<File>()
    var mpsHome: File? = null
    var mpsHeapSize: String = "1g"
    val includedLanguages: MutableSet<String> = HashSet()
    val includedLanguageNamespaces: MutableSet<String> = HashSet()
    val includedConcepts: MutableSet<String> = HashSet()
    val includedModules: MutableSet<String> = HashSet()
    var kotlinDir: File? = null
    var modelqlKotlinDir: File? = null
    var kotlinProject: Project? = null
        set(value) {
            if (kotlinDir == null && value != null) {
                kotlinDir = value.projectDir.resolve("src/main/kotlin_gen")
            }
            field = value
        }
    var typescriptDir: File? = null
    var registrationHelperName: String? = null
    val taskDependencies: MutableList<Any> = ArrayList()

    internal val nameConfig = NameConfig()

    fun names(action: Action<NameConfig>) {
        action.execute(nameConfig)
    }

    var jsonDir: File? = null

    fun dependsOn(vararg dependency: Any) {
        taskDependencies.addAll(dependency)
    }

    fun javaExecutable(provider: () -> File) {
        javaExecutableProvider = provider
    }

    fun javaExecutable(file: File) {
        javaExecutableProvider = { file }
    }

    fun modulesFrom(dir: File) {
        moduleFolders += dir
    }

    fun includeLanguage(fqName: String) {
        includedLanguages += fqName
    }

    fun includeNamespace(languagePrefix: String) {
        includedLanguageNamespaces += languagePrefix
    }

    fun includeConcept(fqName: String) {
        includedConcepts += fqName
    }

    fun exportModules(namePrefix: String) {
        includedModules += namePrefix
    }
}
