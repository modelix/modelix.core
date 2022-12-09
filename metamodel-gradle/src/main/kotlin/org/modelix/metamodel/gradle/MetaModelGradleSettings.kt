package org.modelix.metamodel.gradle

import org.gradle.api.Project
import java.io.File

open class MetaModelGradleSettings {
    var javaExecutable: File? = null
    val moduleFolders = ArrayList<File>()
    var mpsHome: File? = null
    val includedLanguages: MutableSet<String> = HashSet()
    val includedLanguageNamespaces: MutableSet<String> = HashSet()
    val includedConcepts: MutableSet<String> = HashSet()
    var kotlinDir: File? = null
    var typescriptDir: File? = null
    var registrationHelperName: String? = null

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