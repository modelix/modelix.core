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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.messages.MessageBusConnection
import jetbrains.mps.ide.project.ProjectHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.area.PArea
import org.modelix.model.client.ActiveBranch
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.binding.Binding
import org.modelix.mps.sync.binding.ModuleBinding
import org.modelix.mps.sync.binding.ProjectBinding
import org.modelix.mps.sync.binding.RootBinding
import org.modelix.mps.sync.replication.CloudRepository
import org.modelix.mps.sync.replication.MpsReplicatedRepository
import java.net.ConnectException
import java.net.URL

// status: ready to test
class ModelServerConnectionV2 constructor(override val baseUrl: String) : ModelServerConnectionInterface {
    private val logger = logger<ModelServerConnectionV2>()
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    private val messageBusConnection: MessageBusConnection
    private val client2: ModelClientV2

    companion object {
        val uiStateRepositoryId = RepositoryId("uistate")
        val DEFAULT_REPOSITORY_ID = "default"
    }
    private var infoTree: MpsReplicatedRepository? = null
    private val activeBranches = mutableMapOf<RepositoryId, ActiveBranch>()
    private val listeners = mutableListOf<IModelServerConnectionListener>()
    var id: String? = null
        private set
    var email: String? = null
        private set

    private val bindings = mutableMapOf<RepositoryId, RootBinding>()

    override fun toString() = this.baseUrl

    constructor(baseUrl: URL) : this(baseUrl.toString())

    init {
        logger.info("ModelServerConnectionV2.init($baseUrl)")

        // we register on projectClosing to remove our bindings
        messageBusConnection = ApplicationManager.getApplication().messageBus.connect()
        messageBusConnection.subscribe(
            ProjectManager.TOPIC,
            object : ProjectManagerListener {
                override fun projectClosing(closingProject: Project) {
                    bindings.values.flatMap { it.getAllBindings() }.filterIsInstance<ProjectBinding>()
                        .filter { ProjectHelper.toIdeaProject(it.mpsProject) == closingProject }.forEach {
                            removeBinding(it)
                        }
                }
            },
        )

//        todo: token handling
//        val workspaceTokenProvider = { InstanceJwtToken.token }
        client2 = ModelClientV2.builder().url(baseUrl).build()
        initializeClient()
        logger.info("Connected to $baseUrl")
    }

    private fun initializeClient() {
        // actually initialize aka connect to the client
        runBlocking(coroutineScope.coroutineContext) {
            try {
                logger.info("Connecting to $baseUrl")
                client2.init()
            } catch (e: ConnectException) {
                logger.warn("Unable to connect: ${e.message} / ${e.cause}")
                throw e
            }
        }
    }

    override fun isConnected(): Boolean {
        return client2.isActive()
    }

    private fun ensureConnected() {
        if (!this.isConnected()) {
            this.initializeClient()
        }
    }

    override fun getAuthor(): String {
        // todo: legacy? how is this handled in clientv2?
        return "null"
    }

    // todo: relevant for testing later
    fun getRepositoryCount(): Int = runBlocking(coroutineScope.coroutineContext) { client2.listRepositories().count() }

    override fun addRepository(id: String) {
        runBlocking(coroutineScope.coroutineContext) {
            client2.initRepository(RepositoryId(id))
        }
    }

    override fun removeRepository(id: String) {
        runBlocking(coroutineScope.coroutineContext) { client2.deleteRepository(RepositoryId(id)) }
    }

    override fun trees(): List<CloudRepository> {
        TODO("Not yet implemented")
    }

    override fun addBinding(repositoryId: RepositoryId, binding: Binding, callback: Runnable?) {
        TODO("Not yet implemented")
    }

    fun getClient(): IModelClientV2 {
        ensureConnected()
        return client2
    }

    // --------------------------------------------------------------------------

    // todo: question to sascha: where is model server info stored now? how to access it? still relevant?
    fun getInfo(): INode {
        ensureConnected()
        var result: INode? = PArea(infoTree!!.branch).executeRead {
            val transaction = infoTree!!.branch.transaction
            val allChildren = transaction.getAllChildren(ITree.ROOT_ID).map { PNodeAdapter(it, infoTree!!.branch) }
            allChildren.firstOrNull { it.concept == BuiltinLanguages.ModelixRuntimelang.ModelServerInfo }
        }
        if (result == null) {
            result = PArea(infoTree!!.branch).executeWrite {
                val transaction = infoTree!!.branch.writeTransaction
                val id = transaction.addNewChild(
                    ITree.ROOT_ID,
                    "info",
                    -1,
                    BuiltinLanguages.ModelixRuntimelang.ModelServerInfo,
                )
                addRepository(ModelServerConnection.DEFAULT_REPOSITORY_ID)
                PNodeAdapter(id, infoTree!!.branch)
            }
        }
        return result
    }

    override fun getRootBinding(repositoryId: RepositoryId): RootBinding {
        var rootBinding = bindings[repositoryId]
        if (rootBinding == null) {
            rootBinding = RootBinding(this, repositoryId)
            bindings[repositoryId] = rootBinding
        }
        return rootBinding
    }

//    private fun onConnect() {
//        id = client2.get["server-id"]
//        if (id?.isEmpty() == true) {
//            // TODO 'repositoryId' was renamed to 'server-id'. After migrating all servers this request is not required anymore.
//            id = client["repositoryId"]
//        }
//        check(id?.isEmpty() == false) { "$baseUrl doesn't provide an ID" }
//        if (infoTree == null) {
//            infoTree =
//                MpsReplicatedRepository(client, infoRepositoryId, ActiveBranch.DEFAULT_BRANCH_NAME) { getAuthor() }
//        }
//        try {
//            email = client.getEmail()
//        } catch (ex: Exception) {
//            logger.error("Failed to read the users e-mail address", ex)
//        }
//        logger.debug("connected to $baseUrl")
//    }

    fun getRepositoryInfoById(repositoryId: String): INode {
        val repositoryInfo = PArea(getInfoBranch()).executeRead {
            val modelServerInfo = getInfo()
            modelServerInfo.getChildren(BuiltinLanguages.ModelixRuntimelang.ModelServerInfo.repositories)
                .firstOrNull { it.getPropertyValue(BuiltinLanguages.ModelixRuntimelang.RepositoryInfo.id) == repositoryId }
        }
        if (repositoryInfo == null) {
            val knownRepositoryIds = PArea(getInfoBranch()).executeRead {
                val modelServerInfo = getInfo()
                modelServerInfo.getChildren(BuiltinLanguages.ModelixRuntimelang.ModelServerInfo.repositories)
                    .map { it.getPropertyValue(BuiltinLanguages.ModelixRuntimelang.RepositoryInfo.id) }
            }

            throw IllegalArgumentException("RepositoryInfo with ID $repositoryId not found. Known repository ids: $knownRepositoryIds")
        }
        return repositoryInfo
    }

    fun getModuleBinding(repositoryId: RepositoryId, moduleNodeId: Long): List<ModuleBinding> =
        getModuleBindings().filter {
            it.getCloudRepository()?.getRepositoryId() == repositoryId && it.moduleNodeId == moduleNodeId
        }

    fun getModuleBindings() = bindings.values.flatMap { it.getAllBindings() }.filterIsInstance<ModuleBinding>()

    override fun getInfoBranch(): IBranch {
        TODO("Not yet implemented")

        // todo obsolete? where to get meta info from?
    }

    fun <T> computeRead(repositoryId: RepositoryId, producer: () -> T) = this.getInfoBranch().computeRead {
        val activeBranch = this.getActiveBranch(repositoryId)
        val branch = activeBranch.branch
        PArea(branch).executeRead { producer.invoke() }
    }

    override fun getActiveBranch(repositoryId: RepositoryId): ActiveBranch {
        // todo use
//        ReplicatedModel.getBranch ?
//        client2.getReplicatedModel(BranchReference()).
        TODO("Not yet implemented")

//        ensureConnected()
//        synchronized(activeBranches) {
//            var activeBranch = activeBranches[repositoryId]
//            if (activeBranch == null) {
//                activeBranch = object : ActiveBranch(client, repositoryId, null, { getAuthor() }) {
//                    override fun createReplicatedRepository(
//                        client: IModelClient,
//                        repositoryId: RepositoryId,
//                        branchName: String,
//                        user: () -> String,
//                    ): ReplicatedRepository =
//                        MpsReplicatedRepository(client, repositoryId, branchName) { user.invoke() }
//                }
//                activeBranches[repositoryId] = activeBranch
//            }
//            return activeBranch
//        }
    }

    override fun getActiveBranches(): Iterable<ActiveBranch> {
        TODO("Not yet implemented")
    }

    override fun dispose() {
        synchronized(this) {
            try {
                messageBusConnection.disconnect()
            } catch (ex: Exception) {
                logger.error(ex.message, ex)
            }
            try {
                client2.close()
            } catch (ex: Exception) {
                logger.error(ex.message, ex)
            }
            try {
                infoTree?.dispose()
            } catch (ex: Exception) {
                logger.error(ex.message, ex)
            }

            bindings.values.forEach { it.deactivate(null) }
            synchronized(activeBranches) {
                activeBranches.values.forEach {
                    try {
                        it.dispose()
                    } catch (ex: Exception) {
                        logger.error(ex.message, ex)
                    }
                }
                activeBranches.clear()
            }
        }
    }

    // ----------------- these need to be migrated i guess

    fun addListener(listener: IModelServerConnectionListener) {
        println("--- listener added: $listener")
        listeners.add(listener)
    }

    fun removeListener(listener: IModelServerConnectionListener) {
        listeners.remove(listener)
    }
    override fun addBinding(repositoryId: RepositoryId, binding: Binding) {
        addBinding(repositoryId, binding, null)
    }
    override fun removeBinding(binding: Binding) {
        binding.deactivate(null)
        binding.owner = null
    }
}
