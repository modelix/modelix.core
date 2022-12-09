package org.modelix.metamodel.gradle

import org.gradle.api.Project
import java.io.File

open class MetaModelGradleSettings {
    var javaExecutable: File? = null
    val moduleFolders = ArrayList<File>()
    var mpsHome: File? = null

    fun modulesFrom(dir: File) {
        moduleFolders += dir
    }
}