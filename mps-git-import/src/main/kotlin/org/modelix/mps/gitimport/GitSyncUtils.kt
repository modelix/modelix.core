package org.modelix.mps.gitimport

import jetbrains.mps.vfs.IFile
import org.modelix.model.mpsadapters.MPSProjectAsNode

object GitSyncUtils {
    fun collectProjectDirs(file: IFile, result: MutableList<IFile> = ArrayList()): List<IFile> {
        if (file.isDirectory) {
            if (file.name == ".mps") {
                result.add(file.parent!!)
            }
            for (child in file.children ?: emptyList()) {
                collectProjectDirs(child, result)
            }
        }
        return result
    }

    fun <R> runWithProjects(rootDir: IFile, mpsRepo: DummyRepo, body: () -> R): R {
        val projectDirs = collectProjectDirs(rootDir)
        println("Project directories: $projectDirs")
        val mpsProjects = projectDirs.map { DummyMPSProject(mpsRepo, it) }
        return MPSProjectAsNode.runWithProjects(mpsProjects, body)
    }
}
