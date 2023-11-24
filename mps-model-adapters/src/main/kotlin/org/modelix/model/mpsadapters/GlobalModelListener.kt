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
package org.modelix.model.mpsadapters

import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SDependency
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleListener
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.module.SRepositoryListener

abstract class GlobalModelListener {
    @Suppress("removal")
    protected var repositoryListener: SRepositoryListener = object : SRepositoryListener {
        override fun moduleAdded(m: SModule) {
            start(m)
        }

        override fun beforeModuleRemoved(m: SModule) {
            stop(m)
        }

        override fun moduleRemoved(p0: SModuleReference) {}
        override fun commandStarted(p0: SRepository?) {}
        override fun commandFinished(p0: SRepository?) {}
        override fun updateStarted(p0: SRepository?) {}
        override fun updateFinished(p0: SRepository?) {}
        override fun repositoryCommandStarted(p0: SRepository?) {}
        override fun repositoryCommandFinished(p0: SRepository?) {}
    }

    @Suppress("removal")
    protected var moduleListener: SModuleListener = object : SModuleListener {
        override fun modelAdded(module: SModule, model: SModel) {
            start(model)
        }

        override fun beforeModelRemoved(module: SModule, model: SModel) {
            stop(model)
        }

        override fun modelRemoved(p0: SModule?, p1: SModelReference?) {}
        override fun beforeModelRenamed(p0: SModule?, p1: SModel?, p2: SModelReference?) {}
        override fun modelRenamed(p0: SModule?, p1: SModel?, p2: SModelReference?) {}
        override fun dependencyAdded(p0: SModule?, p1: SDependency?) {}
        override fun dependencyRemoved(p0: SModule?, p1: SDependency?) {}
        override fun languageAdded(p0: SModule?, p1: SLanguage?) {}
        override fun languageRemoved(p0: SModule?, p1: SLanguage?) {}
        override fun moduleChanged(p0: SModule?) {}
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
