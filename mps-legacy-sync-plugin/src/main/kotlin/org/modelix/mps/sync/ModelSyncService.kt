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
import com.intellij.openapi.util.Disposer
import io.ktor.client.HttpClient
import io.ktor.http.Url
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.delay
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.project.Project
import org.modelix.model.api.IChildLink
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.getRootNode
import org.modelix.model.client.ActiveBranch
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.mps.SNodeToNodeAdapter
import org.modelix.model.mpsplugin.Binding
import org.modelix.model.mpsplugin.ModelServerConnections
import org.modelix.model.mpsplugin.ModuleBinding
import org.modelix.model.mpsplugin.ProjectBinding
import org.modelix.model.mpsplugin.ProjectModuleBinding
import org.modelix.model.mpsplugin.SyncDirection
import org.modelix.model.mpsplugin.TransientModuleBinding
import org.modelix.model.mpsplugin.plugin.Mpsplugin_ApplicationPlugin
import org.modelix.mps.sync.api.IBranchConnection
import org.modelix.mps.sync.api.IModelServerConnection
import org.modelix.mps.sync.api.IModuleBinding
import org.modelix.mps.sync.api.ISyncService
import java.util.Collections
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@Service(Service.Level.APP)
class ModelSyncService : Disposable, ISyncService {

    companion object {
        var INSTANCE: ModelSyncService? = null
    }

    private var projects: Set<com.intellij.openapi.project.Project> = emptySet()
    private val legacyAppPluginParts = listOf(
        org.modelix.model.mpsadapters.plugin.ApplicationPlugin_AppPluginPart(),
        org.modelix.model.mpsplugin.plugin.ApplicationPlugin_AppPluginPart(),
    )
    private var serverConnections: List<ServerConnection> = emptyList()

    init {
        check(INSTANCE == null) { "Single instance expected" }
        INSTANCE = this
        Mpsplugin_ApplicationPlugin().let {
            it.createGroups()
            it.adjustRegularGroups()
        }

        legacyAppPluginParts.forEach { it.init() }
    }

    override fun dispose() {
        INSTANCE = null
        // serverConnections disposal is handled by Disposer
        legacyAppPluginParts.forEach { it.dispose() }
    }

    fun registerProject(project: com.intellij.openapi.project.Project) {
        projects += project
    }

    fun unregisterProject(project: com.intellij.openapi.project.Project) {
        projects -= project
    }

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
            private val bindings: MutableList<org.modelix.mps.sync.api.IBinding> = Collections.synchronizedList(ArrayList())

            override fun getServerConnection(): IModelServerConnection {
                return this@ServerConnection
            }

            override fun switchBranch(branchName: String) {
                legacyActiveBranch.switchBranch(branchName)
            }

            override fun bindProject(mpsProject: Project, existingProjectNodeId: Long?): org.modelix.mps.sync.api.IBinding {
                val mpsProject = mpsProject as MPSProject
                val legacyBinding = if (existingProjectNodeId == null) {
                    ProjectBinding(mpsProject, 0L, SyncDirection.TO_CLOUD)
                } else {
                    ProjectBinding(mpsProject, existingProjectNodeId, SyncDirection.TO_MPS)
                }
                legacyConnection.addBinding(repositoryId, legacyBinding)
                return ProjectBindingAdapter(legacyBinding).also {
                    Disposer.register(this, it)
                    bindings += it
                }
            }

            override fun bindModule(mpsModule: SModule?, existingModuleNodeId: Long?): IModuleBinding {
                val direction = when {
                    mpsModule == null -> SyncDirection.TO_MPS
                    existingModuleNodeId == null -> SyncDirection.TO_CLOUD
                    else -> throw IllegalArgumentException("One of 'mpsModule' or 'existingModuleNodeId' must be provided")
                }
                val legacyBinding = ProjectModuleBinding(existingModuleNodeId ?: 0L, mpsModule, direction)
                legacyConnection.addBinding(repositoryId, legacyBinding)
                return ModuleBindingAdapter(legacyBinding).also {
                    Disposer.register(this, it)
                    bindings += it
                }
            }

            override fun bindTransientModule(existingModuleNodeId: Long): IModuleBinding {
                val legacyBinding = TransientModuleBinding(existingModuleNodeId)
                legacyConnection.addBinding(repositoryId, legacyBinding)
                return ModuleBindingAdapter(legacyBinding).also {
                    Disposer.register(this, it)
                    bindings += it
                }
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

            abstract inner class BindingAdapter : org.modelix.mps.sync.api.IBinding {
                abstract val legacyBinding: Binding
                override fun getConnection(): IBranchConnection {
                    return this@ActiveBranchAdapter
                }

                @OptIn(ExperimentalTime::class)
                override suspend fun flush() {
                    legacyBinding.rootBinding.syncQueue.flush()
                    delay(5.seconds) // TODO wait until the client is done writing to the server
                }

                override fun dispose() {
                    legacyBinding.deactivate(null)
                }
            }

            inner class ProjectBindingAdapter(override val legacyBinding: ProjectBinding) : BindingAdapter()

            inner class ModuleBindingAdapter(override val legacyBinding: ModuleBinding) : BindingAdapter(), org.modelix.mps.sync.api.IModuleBinding {
                override fun getModule(): SModule = legacyBinding.module
            }
        }
    }
}
