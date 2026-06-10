package org.modelix.model.mpsadapters

import com.intellij.openapi.project.ex.ProjectEx
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.module.ModuleDeleteHelper
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.ProjectBase
import jetbrains.mps.vfs.openapi.FileSystem
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.project.Project
import org.modelix.mps.api.ModelixMpsApi

/**
 * There is no stable interface in MPS that provides all the methods needed.
 */
interface IMPSProject {
    fun getName(): String
    fun setName(name: String)
    fun getVirtualFolder(module: SModule): String?
    fun setVirtualFolder(module: SModule, folder: String?)
    fun getRepository(): SRepository
    fun addModule(module: SModule)
    fun removeModule(module: SModule)
    fun deleteModule(module: SModule)
    fun getModules(): List<SModule>

    /**
     * Returns the project's file system.
     *
     * Uses the openapi [FileSystem] interface as the common type across MPS versions: since 2025.1 the
     * project's file system no longer implements jetbrains.mps.vfs.IFileSystem.
     */
    fun getFileSystem(): FileSystem
    fun getBasePath(): String?
}

data class MPSProjectAdapter(val mpsProject: Project) : IMPSProject {
    override fun getName(): String {
        return (mpsProject as? ProjectBase)?.let { ProjectHelper.toIdeaProject(it).name.takeIf { it.isNotEmpty() } }
            ?: mpsProject.name
    }

    override fun setName(name: String) {
        (ProjectHelper.toIdeaProject(mpsProject as ProjectBase) as ProjectEx).setProjectName(name)
    }

    override fun getVirtualFolder(module: SModule): String? {
        return ModelixMpsApi.getVirtualFolder(mpsProject, module)
    }

    override fun setVirtualFolder(module: SModule, folder: String?) {
        ModelixMpsApi.setVirtualFolder(mpsProject, module, folder)
    }

    override fun getRepository(): SRepository {
        return mpsProject.repository
    }

    override fun addModule(module: SModule) {
        (mpsProject as ProjectBase).addModule(module)
    }

    override fun getModules(): List<SModule> {
        return (mpsProject as ProjectBase).projectModules
    }

    override fun removeModule(module: SModule) {
        (mpsProject as ProjectBase).removeModule(module)
    }

    override fun deleteModule(module: SModule) {
        ModuleDeleteHelper(mpsProject as jetbrains.mps.project.Project).deleteModules(
            listOf(module),
            false,
            true,
        )
    }

    @Suppress("DEPRECATION", "USELESS_CAST")
    override fun getFileSystem(): FileSystem {
        // The cast is required for MPS versions where MPSProject.fileSystem is typed as IFileSystem,
        // which is unrelated to the openapi FileSystem at compile time but always implements it at runtime.
        val fileSystem = checkNotNull((mpsProject as MPSProject).fileSystem) {
            "File system of project $mpsProject is null"
        }
        return fileSystem as FileSystem
    }

    override fun getBasePath(): String? {
        return ProjectHelper.toIdeaProject(mpsProject as jetbrains.mps.project.Project).getBasePath()
    }
}
