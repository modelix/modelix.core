package org.modelix.mps.sync.connection

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.area.PArea
import org.modelix.model.client.ActiveBranch
import org.modelix.model.client.ConnectionListener
import org.modelix.model.client.ConnectionStatusListener
import org.modelix.model.client.IModelClient
import org.modelix.model.client.ReplicatedRepository
import org.modelix.model.client.RestWebModelClient
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.MpsReplicatedRepository
import org.modelix.mps.sync.binding.Binding
import org.modelix.mps.sync.binding.ModuleBinding
import org.modelix.mps.sync.binding.ProjectBinding
import org.modelix.mps.sync.binding.RootBinding
import org.modelix.mps.sync.plugin.init.EModelixExecutionMode
import org.modelix.mps.sync.plugin.init.ModelixConfigurationSystemProperties
import org.modelix.mps.sync.util.ModelixNotifications.notifyError
import java.net.URL
import java.util.function.Consumer
import javax.swing.SwingUtilities

// status: migrated, but needs some bugfixes
class ModelServerConnection {

    companion object {
        val uiStateRepositoryId = RepositoryId("uistate")
        val DEFAULT_REPOSITORY_ID = "default"

        private val infoRepositoryId = RepositoryId("info")
        private val settingsKeyPrefix = ModelServerConnection::class.java.getName() + ".token/"
    }

    private val logger = mu.KotlinLogging.logger {}

    val baseUrl: String
    private val client: RestWebModelClient
    private var infoTree: MpsReplicatedRepository? = null
    private val activeBranches = mutableMapOf<RepositoryId, ActiveBranch>()
    private val listeners = mutableListOf<IModelServerConnectionListener>()
    var id: String? = null
        private set
    var email: String? = null
        private set
    private val messageBusConnection: MessageBusConnection
    private val bindings = mutableMapOf<RepositoryId, RootBinding>()

    constructor(baseUrl: String) {
        logger.debug { "ModelServerConnection.init($baseUrl)" }
        messageBusConnection = ApplicationManager.getApplication().messageBus.connect()
        // TODO fixme: ProjectManager.TOPIC does not exist. Maybe we use the wrong jar?
        // ProjectManager.TOPIC
        val topic: Topic<ProjectManagerListener> = null!!
        messageBusConnection.subscribe(
            topic,
            object : ProjectManagerListener {

                override fun projectClosing(closingProject: Project) {
                    bindings.values.flatMap { it.getAllBindings() }.filterIsInstance<ProjectBinding>()
                        .filter { ProjectHelper.toIdeaProject(it.mpsProject) == closingProject }.forEach {
                            removeBinding(it)
                        }
                }

                override fun projectOpened(p0: Project) {}

                override fun canCloseProject(p0: Project): Boolean = true

                override fun projectClosed(p0: Project) {}
            },
        )

        this.baseUrl = baseUrl
        val workspaceTokenProvider = { InstanceJwtToken.token }
        val tokenProvider =
            if (ModelixConfigurationSystemProperties.getExecutionMode() == EModelixExecutionMode.PROJECTOR) {
                workspaceTokenProvider
            } else {
                null
            }
        client = RestWebModelClient(baseUrl, tokenProvider, listOf(ConnectionListenerForForbiddenMessage(baseUrl)))

        var connectedFirstTime = true
        client.addStatusListener(object : ConnectionStatusListener {
            override fun invoke(
                oldValue: RestWebModelClient.ConnectionStatus,
                newValue: RestWebModelClient.ConnectionStatus,
            ) {
                if (newValue == RestWebModelClient.ConnectionStatus.CONNECTED && connectedFirstTime) {
                    connectedFirstTime = false
                    onConnect()
                }
                listeners.forEach {
                    it.connectionStatusChanged(newValue == RestWebModelClient.ConnectionStatus.CONNECTED)
                }
            }
        })
    }

    constructor(baseUrl: URL) : this(baseUrl.toString())

    fun getRootBinding(repositoryId: RepositoryId): RootBinding {
        var rootBinding = bindings[repositoryId]
        if (rootBinding == null) {
            rootBinding = RootBinding(this, repositoryId)
            bindings[repositoryId] = rootBinding
        }
        return rootBinding
    }

    @Deprecated("")
    fun reconnect() = client.reconnect()

    private fun onConnect() {
        id = client["server-id"]
        if (id?.isEmpty() == true) {
            // TODO 'repositoryId' was renamed to 'server-id'. After migrating all servers this request is not required anymore.
            id = client["repositoryId"]
        }
        check(id?.isEmpty() == false) { "$baseUrl doesn't provide an ID" }
        if (infoTree == null) {
            infoTree =
                MpsReplicatedRepository(client, infoRepositoryId, ActiveBranch.DEFAULT_BRANCH_NAME) { getAuthor() }
        }
        try {
            email = client.getEmail()
        } catch (ex: Exception) {
            logger.error(ex) { "Failed to read the users e-mail address" }
        }
        logger.debug { "connected to $baseUrl" }
    }

    private fun getAuthor(): String {
        var email = this.email
        if (email == "<no email>") {
            email = null
        }
        return AuthorOverride.apply(email) ?: "null"
    }

    fun isConnected(): Boolean {
        val status = client.connectionStatus
        return status != RestWebModelClient.ConnectionStatus.NEW && status != RestWebModelClient.ConnectionStatus.WAITING_FOR_TOKEN
    }

    private fun checkConnected() {
        val connectionStatus = client.connectionStatus
        if (connectionStatus != RestWebModelClient.ConnectionStatus.CONNECTED) {
            client.reconnect()
        }
        check(isConnected()) { "Not connected. Client is in status $connectionStatus" }
    }

    fun getAllRepositories(): Iterable<INode> =
        PArea(getInfoBranch()).executeWrite { getInfo().getChildren(BuiltinLanguages.ModelixRuntimelang.ModelServerInfo.repositories) }

    fun getAllRepositoriesCount(): Int = getAllRepositories().count()

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

    fun addRepository(id: String): INode {
        // TODO should return org.modelix.model.runtimelang.structure.RepositoryInfo
        return PArea(getInfoBranch()).executeWrite {
            /*val modelServerInfo = getInfo()

             TODO fixme SNodeAPI...
             val repositoryInfo = SNodeAPI.addNewChild(modelServerInfo, BuiltinLanguages.ModelixRuntimelang.ModelServerInfo.repositories)
             repositoryInfo.id = id;
             node<BranchInfo> branchInfo = SNodeAPI.addNewChild(repositoryInfo, BuiltinLanguages.ModelixRuntimelang.RepositoryInfo.branches):BranchInfo;
             branchInfo.name = ActiveBranch.DEFAULT_BRANCH_NAME;
             return repositoryInfo;*/
            Any() as INode
        }
    }

    fun removeRepository(id: String) {
        PArea(getInfoBranch()).executeWrite {
            val info = getInfo()
            val node = info.getChildren(BuiltinLanguages.ModelixRuntimelang.ModelServerInfo.repositories)
                .firstOrNull { it.getPropertyValue(BuiltinLanguages.ModelixRuntimelang.RepositoryInfo.id) == id }
            // TODO NodeToSNodeAdapter.getWrapped() is missing
            val snode: SNode = null!!
            SNodeOperations.deleteNode(snode)
        }
    }

    fun hasProjectBinding(repositoryId: RepositoryId, projectNodeId: Long) = getProjectBindings().any {
        it.getCloudRepository()?.getRepositoryId() == repositoryId && it.projectNodeId == projectNodeId
    }

    fun getProjectBindings() = bindings.values.flatMap { it.getAllBindings() }.filterIsInstance<ProjectBinding>()

    fun addBinding(repositoryId: RepositoryId, binding: Binding, callback: Runnable?) {
        binding.owner = getRootBinding(repositoryId)
        binding.activate(callback)
    }

    fun addBinding(repositoryId: RepositoryId, binding: Binding) {
        addBinding(repositoryId, binding, null)
    }

    private fun removeBinding(binding: Binding) {
        binding.deactivate(null)
        binding.owner = null
    }

    fun hasModuleBinding(repositoryId: RepositoryId, moduleNodeId: Long) =
        getModuleBinding(repositoryId, moduleNodeId).isNotEmpty()

    fun getModuleBinding(repositoryId: RepositoryId, moduleNodeId: Long): List<ModuleBinding> =
        getModuleBindings().filter {
            it.getCloudRepository()?.getRepositoryId() == repositoryId && it.moduleNodeId == moduleNodeId
        }

    fun getModuleBindings() = bindings.values.flatMap { it.getAllBindings() }.filterIsInstance<ModuleBinding>()

    fun getInfoBranch(): IBranch {
        checkConnected()
        return infoTree?.branch!!
    }

    fun <T> computeRead(repositoryId: RepositoryId, producer: () -> T) = this.getInfoBranch().computeRead {
        val activeBranch = this.getActiveBranch(repositoryId)
        val branch = activeBranch.branch
        PArea(branch).executeRead { producer.invoke() }
    }

    fun getInfo(): INode {
        // TODO should return org.modelix.model.runtimelang.structure.ModelServerInfo

        checkConnected()
        var result = PArea(infoTree!!.branch).executeRead {
            val transaction = infoTree!!.branch.transaction
            val allChildren_ = transaction.getAllChildren(ITree.ROOT_ID)
            // TODO fixme. org.modelix.model.mpsadapters.mps.NodeToSNodeAdapter is not found...
            // val allChildren = allChildren_.map{NodeToSNodeAdapter.wrap(new PNodeAdapter(it, infoTree.getBranch())) }
            // TODO should return org.modelix.model.runtimelang.structure.ModelServerInfo
            // return allChildren.filterIsInstance<ModelServerInfo().first()
            Any()
        }
        if (result == null) {
            result = PArea(infoTree!!.branch).executeWrite {
                val transaction = infoTree!!.branch.writeTransaction
                // TODO fix parameter. Problem SConceptAdapter.wrap does not exist anymore in modelix...
                // transaction.addNewChild(ITree.ROOT_ID, "info", -1, SConceptAdapter.wrap(concept/ModelServerInfo/))
                val id = 0L

                /**
                 * TODO fixme:
                 * 1. org.modelix.model.mpsadapters.mps.NodeToSNodeAdapter is not found
                 * 2. repoInfo must be org.modelix.model.runtimelang.structure.ModelServerInfo
                 */
                // val repoInfo = NodeToSNodeAdapter.wrap(new PNodeAdapter(id, infoTree.getBranch())): ModelServerInfo

                addRepository(DEFAULT_REPOSITORY_ID)

                // repoInfo
                Any()
            }
        }
        // return result
        return null!! as INode
    }

    fun getActiveBranch(repositoryId: RepositoryId): ActiveBranch {
        checkConnected()
        synchronized(activeBranches) {
            var activeBranch = activeBranches[repositoryId]
            if (activeBranch == null) {
                activeBranch = object : ActiveBranch(client, repositoryId, null, { getAuthor() }) {
                    override fun createReplicatedRepository(
                        client: IModelClient,
                        repositoryId: RepositoryId,
                        branchName: String,
                        user: () -> String,
                    ): ReplicatedRepository =
                        MpsReplicatedRepository(client, repositoryId, branchName) { user.invoke() }
                }
                activeBranches[repositoryId] = activeBranch
            }
            return activeBranch
        }
    }

    fun getActiveBranches(): Iterable<ActiveBranch> {
        synchronized(activeBranches) {
            return activeBranches.values
        }
    }

    fun dispose() {
        synchronized(this) {
            try {
                messageBusConnection.disconnect()
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
            try {
                client.dispose()
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }
            try {
                infoTree?.dispose()
            } catch (ex: Exception) {
                logger.error(ex) { ex.message }
            }

            bindings.values.forEach { it.deactivate(null) }
            synchronized(activeBranches) {
                activeBranches.values.forEach {
                    try {
                        it.dispose()
                    } catch (ex: Exception) {
                        logger.error(ex) { ex.message }
                    }
                }
                activeBranches.clear()
            }
        }
    }

    fun getClient(): IModelClient {
        checkConnected()
        return client
    }

    override fun toString() = baseUrl

    fun addListener(listener: IModelServerConnectionListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: IModelServerConnectionListener) {
        listeners.remove(listener)
    }

    fun whenConnected(consumer: Consumer<ModelServerConnection?>) {
        if (isConnected()) {
            consumer.accept(this)
        } else {
            val listener = object : IModelServerConnectionListener {
                override fun connectionStatusChanged(connected: Boolean) {
                    if (connected) {
                        consumer.accept(this@ModelServerConnection)
                        removeListener(this)
                    }
                }
            }
            addListener(listener)
        }
    }

    fun trees() = PArea(this.getInfoBranch()).executeRead {
        // We want to obtain a list within the transaction.
        // A sequence (which is lazy) would not work
        val info = this.getInfo()
        info.getChildren(BuiltinLanguages.ModelixRuntimelang.ModelServerInfo.repositories).map {
            val repositoryId =
                RepositoryId(it.getPropertyValue(BuiltinLanguages.ModelixRuntimelang.RepositoryInfo.id)!!)
            CloudRepository(this, repositoryId)
        }
    }
}

interface IModelServerConnectionListener {
    fun connectionStatusChanged(connected: Boolean) {}
    fun bindingAdded(binding: Binding) {}
    fun bindingRemoved(binding: Binding) {}
    fun bindingActivated(binding: Binding) {}
    fun bindingDeactivated(binding: Binding) {}
}

/**
 * It seems that several connections are open at the same time: we do not want to show
 * error messages multiple times, so we use a shared state of the connection
 */
private class ConnectionListenerForForbiddenMessage(private val baseUrl: String) : ConnectionListener {

    companion object {
        private val inForbiddenStateByURL = mutableMapOf<String, Boolean>()
    }

    override fun receivedForbiddenResponse() {
        if (!inForbiddenState()) {
            inForbiddenStateByURL[baseUrl] = true
            SwingUtilities.invokeLater {
                notifyError(
                    "Forbidden Access",
                    "Unauthorized to connect to Model Server $baseUrl. Check you are logged in and have the right to access that Model Server",
                )
            }
        }
    }

    override fun receivedSuccessfulResponse() {
        inForbiddenStateByURL[baseUrl] = false
    }

    private fun inForbiddenState(): Boolean {
        return if (inForbiddenStateByURL.containsKey(baseUrl)) {
            inForbiddenStateByURL[baseUrl]!!
        } else {
            false
        }
    }
}
