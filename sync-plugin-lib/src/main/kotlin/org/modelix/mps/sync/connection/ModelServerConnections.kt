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

package org.modelix.mps.sync.connection

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.area.CompositeArea
import org.modelix.model.area.IArea
import org.modelix.model.area.PArea
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSArea
import org.modelix.mps.sync.util.CommandHelper.getSRepository
import org.modelix.mps.sync.util.ModelixNotifications
import org.modelix.mps.sync.util.concat
import java.net.URL
import java.util.stream.Stream
import kotlin.streams.toList

// status: ready to test
class ModelServerConnections private constructor() {

    companion object {
        val instance = ModelServerConnections()
    }

    init {
        // todo: do we _really_ need to load these here? investigate and delete eventually
        BuiltinLanguages.getAllLanguages().forEach { it.register() }
    }

    private val logger = logger<ModelServerConnections>()
    val modelServers = mutableListOf<ModelServerConnectionInterface>()
    private val listeners = mutableSetOf<IRepositoriesChangedListener>()

    fun getArea(): IArea = getArea(getSRepository())

    fun getArea(mpsRepository: SRepository): IArea {
        val cloudAreas = getConnectedModelServers().flatMap {
            it.getActiveBranch(ModelServerConnection.uiStateRepositoryId)
            it.getActiveBranches()
        }.map {
            val branch = it.branch
            PArea(branch)
        }
        return CompositeArea(
            Stream.of(MPSArea(mpsRepository)).concat(cloudAreas).toList(),
        )
    }

    fun addListener(listener: IRepositoriesChangedListener) = listeners.add(listener)

    fun removeListener(listener: IRepositoriesChangedListener) = listeners.remove(listener)

    fun existModelServer(url: String) = getModelServer(url) != null

    fun getModelServer(url: String): ModelServerConnectionInterface? {
        return if (!url.endsWith("/")) {
            getModelServer("$url/")
        } else {
            this.modelServers.firstOrNull { url == it.baseUrl }
        }
    }

    fun addModelServer(url: String): ModelServerConnectionInterface {
        if (!url.endsWith("/")) {
            return addModelServer("$url/")
        }
        check(!existModelServer(url)) { "The repository with url $url is already present" }
        return doAddModelServer(URL(url))
    }

    private fun doAddModelServer(url: URL): ModelServerConnectionInterface {
        logger.debug("Adding model-server {}", url)
        val newRepo = ModelServerConnection(url)
        modelServers.add(newRepo)
        try {
            // we do not automatically change the persisted configuration, to avoid cycles
            listeners.forEach {
                it.repositoriesChanged()
            }
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            ModelixNotifications.notifyError("Failure while adding model server $url", ex.message ?: "")
        }
        return newRepo
    }

    fun addModelServerV2(url: String): ModelServerConnectionInterface {
        if (!url.endsWith("/")) {
            return addModelServerV2("$url/")
        }
        check(!existModelServer(url)) { "The repository with url $url is already present" }
        return doAddModelServerV2(URL(url))
    }

    private fun doAddModelServerV2(url: URL): ModelServerConnectionInterface {
        logger.debug("Adding model-server V2 {}", url)
        val newRepo = ModelServerConnectionV2(url)
        modelServers.add(newRepo)
        try {
            // we do not automatically change the persisted configuration, to avoid cycles
            listeners.forEach {
                it.repositoriesChanged()
            }
        } catch (ex: Exception) {
            logger.error(ex.message, ex)
            ModelixNotifications.notifyError("Failure while adding model server $url", ex.message ?: "")
        }
        return newRepo
    }

    fun removeModelServer(repo: ModelServerConnection) {
        modelServers.remove(repo)
        // we do not automatically change the persisted configuration, to avoid cycles
        listeners.forEach {
            it.repositoriesChanged()
        }
    }

    fun getConnectedModelServers() = modelServers.filter { it.isConnected() }

    fun getConnectedTreesInRepositories() = getConnectedModelServers().flatMap { it.trees() }

    fun resolveCloudModel(repositoryId: String): INode {
        val repo = getConnectedModelServers().first()
        val activeBranch = repo.getActiveBranch(RepositoryId(repositoryId))

        return object : PNodeAdapter(ITree.ROOT_ID, activeBranch.branch) {
            override val concept: IConcept = BuiltinLanguages.MPSRepositoryConcepts.Repository
        }
    }

    fun dispose() {
        modelServers.forEach {
            it.dispose()
        }
        modelServers.clear()
    }

    fun ensureModelServerIsPresent(url: String): ModelServerConnectionInterface {
        return getModelServer(url) ?: instance.addModelServer(url)
    }
    fun ensureModelServerIsPresentV2(url: String): ModelServerConnectionInterface {
        return getModelServer(url) ?: instance.addModelServerV2(url)
    }
}

interface IRepositoriesChangedListener {
    fun repositoriesChanged()
}
