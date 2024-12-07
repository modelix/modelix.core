@file:Suppress("removal")

package org.modelix.model.mpsadapters

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleListener
import org.jetbrains.mps.openapi.module.SModuleListenerBase
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.module.SRepositoryListener
import org.jetbrains.mps.openapi.module.SRepositoryListenerBase

abstract class GlobalModelListener {
    protected var repositoryListener: SRepositoryListener = object : SRepositoryListenerBase() {
        override fun moduleAdded(m: SModule) {
            start(m)
        }

        override fun beforeModuleRemoved(m: SModule) {
            stop(m)
        }
    }

    protected var moduleListener: SModuleListener = object : SModuleListenerBase() {
        override fun modelAdded(module: SModule, model: SModel) {
            start(model)
        }

        override fun beforeModelRemoved(module: SModule, model: SModel) {
            stop(model)
        }
    }
    protected var myRepositories: MutableSet<SRepository> = HashSet()
    protected var myModules: MutableSet<SModule> = HashSet()
    protected var myModels: MutableSet<SModel> = HashSet()
    fun start(repo: SRepository) {
        if (myRepositories.contains(repo)) {
            return
        }
        myRepositories.add(repo)
        repo.addRepositoryListener(repositoryListener)
        repo.modelAccess.runReadAction {
            for (module in repo.modules) {
                start(module)
            }
        }
        addListener(repo)
    }

    open fun start(module: SModule) {
        if (myModules.contains(module)) {
            return
        }
        myModules.add(module)
        module.addModuleListener(moduleListener)
        for (model in module.models) {
            start(model)
        }
        addListener(module)
    }

    fun start(model: SModel) {
        if (myModels.contains(model)) {
            return
        }
        myModels.add(model)
        addListener(model)
    }

    protected open fun addListener(repository: SRepository) {}
    protected open fun addListener(module: SModule) {}
    protected abstract fun addListener(model: SModel)
    fun stop() {
        for (repo in myRepositories) {
            repo.modelAccess.runReadAction { stop(repo) }
        }
    }

    fun stop(repo: SRepository) {
        if (!myRepositories.contains(repo)) {
            return
        }
        myRepositories.remove(repo)
        repo.removeRepositoryListener(repositoryListener)
        for (module in repo.modules) {
            stop(module)
        }
        removeListener(repo)
    }

    open fun stop(module: SModule) {
        if (!myModules.contains(module)) {
            return
        }
        myModules.remove(module)
        module.removeModuleListener(moduleListener)
        for (model in module.models) {
            stop(model)
        }
        removeListener(module)
    }

    fun stop(model: SModel) {
        if (!myModels.contains(model)) {
            return
        }
        myModels.remove(model)
        removeListener(model)
    }

    protected open fun removeListener(repository: SRepository) {}
    protected open fun removeListener(module: SModule) {}
    protected abstract fun removeListener(model: SModel)
}
