package org.modelix.metamodel.gradle

import org.gradle.api.Project
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
    val includedLanguages: MutableSet<String> = HashSet()
    val includedLanguageNamespaces: MutableSet<String> = HashSet()
    val includedConcepts: MutableSet<String> = HashSet()
    var kotlinDir: File? = null
    var typescriptDir: File? = null
    var registrationHelperName: String? = null
    val taskDependencies: MutableList<Any> = ArrayList()

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
}