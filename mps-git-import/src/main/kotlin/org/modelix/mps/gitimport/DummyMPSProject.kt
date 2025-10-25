package org.modelix.mps.gitimport

import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.IFileSystem
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.mpsadapters.IMPSProject

class DummyMPSProject(
    val repo: DummyRepo,
    val projectDir: IFile,
) : IMPSProject {

    private var name: String = projectDir.findChild(".mps").findChild(".name").takeIf { it.exists() }
        ?.let { it.openInputStream().use { it.bufferedReader().readText() } }
        ?: "git-export-dummy-project"
    private val modules: MutableSet<SModule> = LinkedHashSet()
    private val virtualFolders: MutableMap<SModule, String> = LinkedHashMap()

    override fun getName(): String {
        return name
    }

    override fun setName(name: String) {
        this.name = name
    }

    override fun getVirtualFolder(module: SModule): String? {
        return virtualFolders[module]
    }

    override fun setVirtualFolder(module: SModule, folder: String?) {
        if (folder == null) {
            virtualFolders.remove(module)
        } else {
            virtualFolders[module] = folder
        }
    }

    override fun getRepository(): SRepository {
        return repo
    }

    override fun addModule(module: SModule) {
        repo.register(module)
        modules.add(module)
    }

    override fun removeModule(module: SModule) {
        repo.unregister(module)
        modules.remove(module)
    }

    override fun deleteModule(module: SModule) {
        removeModule(module)
        // TODO delete files
    }

    override fun getModules(): List<SModule> {
        return modules.toList()
    }

    override fun getFileSystem(): IFileSystem {
        return projectDir.fs
    }

    override fun getBasePath(): String? {
        return projectDir.path
    }
}
