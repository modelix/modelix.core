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
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import jetbrains.mps.project.MPSProject
import jetbrains.mps.smodel.MPSModuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.mps.openapi.project.Project
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.api.runSynchronized
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import java.net.ConnectException
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
@Service(Service.Level.APP)
class ModelSyncService : Disposable {

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
