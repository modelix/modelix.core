package org.modelix.mps.sync
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

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.Url
import jetbrains.mps.project.MPSProject
import jetbrains.mps.smodel.MPSModuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.mps.openapi.project.Project
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IChildLink
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.getRootNode
import org.modelix.model.api.runSynchronized
import org.modelix.model.client.ActiveBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.mpsadapters.mps.SNodeToNodeAdapter
import org.modelix.model.mpsplugin.CloudRepository
import org.modelix.model.mpsplugin.ModelServerConnections
import org.modelix.model.mpsplugin.ProjectBinding
import org.modelix.model.mpsplugin.SyncDirection
import org.modelix.mps.sync.api.IBranchConnection
import org.modelix.mps.sync.api.IModelServerConnection
import org.modelix.mps.sync.api.IModuleBinding
import org.modelix.mps.sync.api.ISyncService
import java.net.ConnectException
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
@Service(Service.Level.APP)
class ModelSyncService : Disposable, ISyncService {

    private var serverConnections: List<ServerConnection> = emptyList()

    override fun getBindings(): List<org.modelix.mps.sync.api.IBinding> {
        TODO("Not yet implemented")
    }

    override fun getConnections(): List<IModelServerConnection> {
        return serverConnections
    }

    @Synchronized
    override fun connectServer(httpClient: HttpClient?, baseUrl: Url): IModelServerConnection {
        return ServerConnection(httpClient, baseUrl.toString()).also {
            Disposer.register(this, it)
            serverConnections += it
        }
    }

    private inner class ServerConnection(httpClient: HttpClient?, url: String) : IModelServerConnection {
        private val legacyConnection = ModelServerConnections.getInstance().addModelServer(url, httpClient)
        private val legacyActiveBranches: MutableMap<RepositoryId, ActiveBranchAdapter> = HashMap()

        override fun getActiveBranches(): List<IBranchConnection> {
            return legacyActiveBranches.values.toList()
        }

        override fun newBranchConnection(branchRef: BranchReference): IBranchConnection {
            val repositoryId = branchRef.repositoryId
            synchronized(legacyActiveBranches) {
                check(legacyActiveBranches[repositoryId] == null) {
                    "Currently, only one branch connection is supported per repository"
                }
                return ActiveBranchAdapter(repositoryId, legacyConnection.getActiveBranch(repositoryId)).also {
                    Disposer.register(this, it)
                    legacyActiveBranches[repositoryId] = it
                }
            }
        }

        override fun listRepositories(): List<RepositoryId> {
            val infoBranch = legacyConnection.infoBranch
            val ids = infoBranch.computeRead {
                legacyConnection.allRepositories.map {
                    SNodeToNodeAdapter.wrap(it).getPropertyValue(IProperty.fromName("id"))
                }
            }
            return ids.filterNotNull().map { RepositoryId(it) }
        }

        override fun listBranches(): List<BranchReference> {
            val infoBranch = legacyConnection.infoBranch
            return infoBranch.computeRead {
                legacyConnection.allRepositories.map { SNodeToNodeAdapter.wrap(it) }
                    .flatMap { repoNode ->
                        val repoId = RepositoryId(repoNode.getPropertyValue(IProperty.fromName("id"))!!)
                        repoNode.getChildren(IChildLink.fromName("branches")).map { branchNode ->
                            repoId.getBranchReference(branchNode.getPropertyValue(IProperty.fromName("name")))
                        }
                    }
            }
        }

        override fun listBranches(repository: RepositoryId): List<BranchReference> {
            return listBranches().filter { it.repositoryId == repository }
        }

        override fun dispose() {
            serverConnections -= this
            ModelServerConnections.getInstance().removeModelServer(legacyConnection)
        }

        private inner class ActiveBranchAdapter(val repositoryId: RepositoryId, val legacyActiveBranch: ActiveBranch) : IBranchConnection {
            private val bindings: MutableList<org.modelix.mps.sync.api.IBinding> = ArrayList()

            override fun getServerConnection(): IModelServerConnection {
                return this@ServerConnection
            }

            override fun switchBranch(branchName: String) {
                legacyActiveBranch.switchBranch(branchName)
            }

            override fun bindProject(mpsProject: Project, existingProjectNodeId: Long?): org.modelix.mps.sync.api.IBinding {
                val mpsProject = mpsProject as MPSProject
                val treeInRepository = CloudRepository(legacyConnection, repositoryId)
                val legacyBinding = if (existingProjectNodeId == null) {
                    ProjectBinding(mpsProject, 0L, SyncDirection.TO_CLOUD)
                } else {
                    ProjectBinding(mpsProject, existingProjectNodeId, SyncDirection.TO_MPS)
                }
                treeInRepository.addBinding(legacyBinding)
                return ProjectBindingAdapter(legacyBinding).also {
                    Disposer.register(this, it)
                    synchronized(bindings) {
                        bindings += it
                    }
                }
            }

            override fun bindTransientModule(): IModuleBinding {
                TODO("Not yet implemented")
            }

            override fun <R> readModel(body: (INode) -> R): R {
                val branch = legacyActiveBranch.branch
                return branch.computeRead { body(branch.getRootNode()) }
            }

            override fun <R> writeModel(body: (INode) -> R): R {
                val branch = legacyActiveBranch.branch
                return branch.computeWrite { body(branch.getRootNode()) }
            }

            override fun dispose() {
                legacyActiveBranches -= repositoryId
                legacyActiveBranch.dispose()
            }

            inner class ProjectBindingAdapter(val legacyBinding: ProjectBinding) : org.modelix.mps.sync.api.IBinding {
                override fun getConnection(): IBranchConnection {
                    return this@ActiveBranchAdapter
                }

                override suspend fun flush() {
                    legacyBinding.rootBinding.syncQueue.flush()
                }

                override fun dispose() {
                    legacyBinding.deactivate(null)
                }
            }
        }
    }

    private var log: Logger = logger<ModelSyncService>()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    var syncService: SyncServiceImpl
    private var existingBindings = mutableListOf<IBinding>()

    fun getBindingList(): List<IBinding> {
        return existingBindings.toMutableList()
    }

    init {
        println("============================================ ModelSyncService init")
        syncService = SyncServiceImpl()

        println("============================================ Registering builtin languages")
        // just a dummy call, the initializer of ILanguageRegistry takes care of the rest...
        ILanguageRepository.default.javaClass
        println("============================================ Registration finished")

        println("============================================ Sync Service initialized $syncService")
    }

    fun connectModelServer(
        httpClient: HttpClient?,
        url: String,
        jwt: String,
        afterActivate: (() -> Unit)?,
    ) {
        coroutineScope.launch {
            connectModelServerSuspending(httpClient, url, jwt)
            afterActivate?.invoke()
        }
    }

    suspend fun connectModelServerSuspending(httpClient: HttpClient?, url: String, jwt: String?) {
        log.info("Connection to server: $url with JWT $jwt")
        syncService.connectToModelServer(httpClient, URL(url), jwt)
        log.info("Connected to server: $url with JWT $jwt")
    }

    fun disconnectServer(
        modelClient: ModelClientV2,
        afterActivate: (() -> Unit)?,
    ) {
        coroutineScope.launch {
            log.info("disconnecting to server: ${modelClient.baseUrl}")
            syncService.disconnectModelServer(modelClient)
            log.info("disconnected server: ${modelClient.baseUrl}")
            afterActivate?.invoke()
        }
    }

    suspend fun bindProject(mpsProject: Project, branch: BranchReference): IBinding {
        TODO()
    }

    fun bindProject(
        client: ModelClientV2,
        theProject: MPSProject,
        branchName: String,
        modelName: String,
        model: INode,
        repositoryID: String,
        afterActivate: (() -> Unit)?,
    ) {
        coroutineScope.launch {
            log.info("Binding to project: $theProject")
            try {
                val newBinding = syncService.bindModel(
                    client,
                    BranchReference(RepositoryId(repositoryID), branchName),
                    modelName,
                    model,
                    theProject,
                    afterActivate,
                )
                existingBindings.add(newBinding)
            } catch (e: ConnectException) {
                log.warn("Unable to connect: ${e.message} / ${e.cause}")
            } catch (e: ClientRequestException) {
                log.warn("Illegal request: ${e.message} / ${e.cause}")
            } catch (e: Exception) {
                log.warn("Pokemon Exception Catching: ${e.message} / ${e.cause}")
            }
            // actual correct place to call after activate
            afterActivate?.invoke()
        }
    }

    private var server: String? = null

    fun deactivateBinding(binding: IBinding) {
        binding.deactivate()
        existingBindings.remove(binding)
    }

    fun ensureStarted() {
        println("============================================  ensureStarted")

        runSynchronized(this) {
            log.info("starting modelix synchronization plugin")
            if (server != null) return

            val rootNodeProvider: () -> INode? = { MPSModuleRepository.getInstance()?.let { MPSRepositoryAsNode(it) } }
            log.info("rootNodeProvider: $rootNodeProvider")
        }
    }

    override fun dispose() {
        println("============================================  dispose")
        syncService.dispose()
        ensureStopped()
    }

    private fun ensureStopped() {
        println("============================================  ensureStopped")
        runSynchronized(this) {
            if (server == null) return
            println("stopping modelix server")
            server = null
        }
    }
}
