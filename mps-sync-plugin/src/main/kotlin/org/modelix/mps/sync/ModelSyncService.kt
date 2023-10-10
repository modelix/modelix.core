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
import io.ktor.client.plugins.ClientRequestException
import jetbrains.mps.project.MPSProject
import jetbrains.mps.smodel.MPSModuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.modelix.model.api.INode
import org.modelix.model.api.runSynchronized
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.mps.sync.binding.IBinding
import java.net.ConnectException
import java.net.URL

@Service(Service.Level.APP)
class ModelSyncService : Disposable {

    private var log: Logger = logger<ModelSyncService>()
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    private var syncService: SyncServiceImpl
    private var existingBindings: MutableList<IBinding> = mutableListOf<IBinding>()

    fun getBindingList(): List<IBinding> {
        return existingBindings.toMutableList()
    }

    init {
        println("============================================ ModelSyncService init")
        syncService = SyncServiceImpl()
        log.info("modelix sync plugin initialized: $syncService")
    }

    fun bindProject(
        theProject: MPSProject,
        url: String,
        repositoryID: String,
        branchName: String,
        modelName: String,
        jwt: String,
        afterActivate: () -> Unit,
    ) {
        coroutineScope.launch {
            log.info("Binding project: $theProject from $url")
            try {
                val newBinding = syncService.bindModel(
                    URL(url),
                    BranchReference(RepositoryId(repositoryID), branchName),
                    modelName,
                    jwt,
                    theProject,
                    afterActivate,
                )
                existingBindings.add(newBinding)
            } catch (e: ConnectException) {
                log.warn("Unable to connect2: ${e.message} / ${e.cause}")
            } catch (e: ClientRequestException) {
                log.warn("Illegal request: ${e.message} / ${e.cause}")
            } catch (e: Exception) {
                log.warn("Pokemon Exception Catching: ${e.message} / ${e.cause}")
            }
            // actual correct place to call after activate
            afterActivate()
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
