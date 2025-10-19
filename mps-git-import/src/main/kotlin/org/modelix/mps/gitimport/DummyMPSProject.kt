package org.modelix.mps.gitimport

import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.persistence.ProjectDescriptorPersistence
import jetbrains.mps.project.structure.project.ModulePath
import jetbrains.mps.util.MacrosFactory
import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.IFileSystem
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.mpsadapters.IMPSProject

class DummyMPSProject(
    val repo: DummyRepo,
    val projectDir: IFile,
) : IMPSProject {

    private var name: String = projectDir.findChild(".mps").findChild(".name").takeIf { it.exists() }
        ?.let { it.openInputStream().use { it.bufferedReader().readText() } }
        ?: "git-export-dummy-project"
    private val resolvedModules: MutableMap<SModuleReference, ModuleEntry> = LinkedHashMap()
    private val moduleEntries = ArrayList<ModuleEntry>()

    init {
        val descriptor = ProjectDescriptorPersistence(projectDir, MacrosFactory.forProjectFile(projectDir)).loadFromFile()
        for (path in descriptor.modulePaths) {
            moduleEntries.add(ModuleEntry(path = path))
        }
    }

    private fun resolveEntry(ref: SModuleReference): ModuleEntry? {
        return resolvedModules[ref] ?: moduleEntries.find { it.resolve()?.moduleReference == ref }
    }

    override fun getName(): String {
        return name
    }

    override fun setName(name: String) {
        this.name = name
    }

    override fun getVirtualFolder(module: SModule): String? {
        return resolveEntry(module.moduleReference)?.path?.virtualFolder
    }

    override fun setVirtualFolder(module: SModule, folder: String?) {
        resolveEntry(module.moduleReference)?.setVirtualFolder(folder)
    }

    override fun getRepository(): SRepository {
        return repo
    }

    override fun addModule(module: SModule) {
        repo.register(module)
        moduleEntries.add(ModuleEntry(module as AbstractModule))
    }

    override fun removeModule(module: SModule) {
        repo.unregister(module)
        moduleEntries.removeIf { it.moduleReference == module.moduleReference }
        resolvedModules.remove(module.moduleReference)
    }

    override fun deleteModule(module: SModule) {
        removeModule(module)
        // TODO delete files
    }

    override fun getModules(): List<SModule> {
        return moduleEntries.mapNotNull { it.resolve() }
    }

    override fun getFileSystem(): IFileSystem {
        return projectDir.fs
    }

    override fun getBasePath(): String? {
        return projectDir.path
    }

    private inner class ModuleEntry(
        var moduleReference: SModuleReference? = null,
        var path: ModulePath,
    ) {
        constructor(module: AbstractModule) : this(module.moduleReference, ModulePath(module.descriptorFile!!))

        fun resolve(): SModule? {
            return moduleReference?.let { repo.getModule(it.moduleId) }
                ?: repo.modules.filterIsInstance<AbstractModule>().firstOrNull { it.descriptorFile?.path == path.file.path }
                    ?.also {
                        moduleReference = it.moduleReference
                        resolvedModules[it.moduleReference] = this
                    }
        }

        fun setVirtualFolder(folder: String?) {
            path = path.withVirtualFolder(folder)
        }
    }
}
