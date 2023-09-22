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

import de.q60.mps.incremental.runtime.DependencyBroadcaster
import de.q60.mps.shadowmodels.runtime.model.persistent.SM_PTree
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.area.CompositeArea
import org.modelix.model.area.IArea
import org.modelix.model.area.PArea
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSArea
import org.modelix.mps.sync.util.CommandHelper.getSRepository
import org.modelix.mps.sync.util.ModelixNotifications
import org.modelix.mps.sync.util.concat
import java.net.URL
import java.util.stream.Stream
import kotlin.streams.toList

// status: migrated, but needs some bugfixes
class ModelServerConnections private constructor() {

    companion object {
        val instance = ModelServerConnections()
        val localUiStateBranch = PBranch(SM_PTree.EMPTY, IdGenerator.Companion.getInstance(1))

        init {
            localUiStateBranch.addListener(object : IBranchListener {
                override fun treeChanged(oldTree: ITree?, newTree: ITree) {
                    if (oldTree == null) {
                        return
                    }
                    val changesCollector = TreeChangesCollector(localUiStateBranch)
                    newTree.visitChanges(oldTree, changesCollector)
                    DependencyBroadcaster.INSTANCE.dependenciesChanged(changesCollector.changes)
                }
            })
        }
    }

    private val logger = mu.KotlinLogging.logger {}
    val modelServers = mutableListOf<ModelServerConnection>()
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
            Stream.of(MPSArea(mpsRepository)).concat(cloudAreas).concat(
                Stream.of(
                    PArea(
                        localUiStateBranch,
                    ),
                ),
            ).toList(),
        )
    }

    fun addListener(listener: IRepositoriesChangedListener) = listeners.add(listener)

    fun removeListener(listener: IRepositoriesChangedListener) = listeners.remove(listener)

    fun existModelServer(url: String) = getModelServer(url) != null

    fun getModelServer(url: String): ModelServerConnection? {
        return if (!url.endsWith("/")) {
            getModelServer("$url/")
        } else {
            this.modelServers.firstOrNull { URL(url) == it.baseUrl }
        }
    }

    fun addModelServer(url: String): ModelServerConnection {
        if (!url.endsWith("/")) {
            return addModelServer("$url/")
        }
        check(!existModelServer(url)) { "The repository with url $url is already present" }
        return doAddModelServer(URL(url))
    }

    private fun doAddModelServer(url: URL): ModelServerConnection {
        val newRepo = ModelServerConnection(url)
        modelServers.add(newRepo)
        try {
            // we do not automatically change the persisted configuration, to avoid cycles
            listeners.forEach {
                it.repositoriesChanged()
            }
        } catch (ex: Exception) {
            logger.error(ex) { ex.message }
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

    fun resolveCloudModel(repositoryId: String): Any {
        // should return org.modelix.model.repositoryconcepts.structure.Repository

        val repo = getConnectedModelServers().first()
        val activeBranch = repo.getActiveBranch(RepositoryId(repositoryId))

        // TODO fixme. org.modelix.model.mpsadapters.mps.NodeToSNodeAdapter is not found...
        /*
        NodeToSNodeAdapter.wrap(new PNodeAdapter(ITree.ROOT_ID, activeBranch.getBranch()) {
            @Override
            public IConcept getConcept() {
                SConceptAdapter.wrap(concept/Repository/);
            }
        }, MPSModuleRepository.getInstance()):Repository;
         */

        return Any()
    }

    fun dispose() {
        modelServers.forEach {
            it.dispose()
        }
        modelServers.clear()
    }

    fun ensureModelServerIsPresent(url: String): ModelServerConnection {
        val modelServerConnection = getModelServer(url)
        return modelServerConnection ?: instance.addModelServer(url)
    }
}

interface IRepositoriesChangedListener {
    fun repositoriesChanged(): Unit
}
