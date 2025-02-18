@file:OptIn(ExperimentalTime::class)

package org.modelix.mps.sync3

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.IVersion
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWrite
import org.modelix.model.lazy.BranchReference
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.mpsadapters.computeRead
import org.modelix.model.sync.bulk.FullSyncFilter
import org.modelix.model.sync.bulk.InvalidatingVisitor
import org.modelix.model.sync.bulk.InvalidationTree
import org.modelix.model.sync.bulk.ModelSynchronizer
import org.modelix.model.sync.bulk.ModelSynchronizer.IIncrementalUpdateInformation
import org.modelix.model.sync.bulk.NodeAssociationFromModelServer
import org.modelix.model.sync.bulk.NodeAssociationToModelServer
import org.modelix.mps.api.ModelixMpsApi
import org.modelix.mps.model.sync.bulk.MPSProjectSyncMask
import org.modelix.mps.sync3.Binding.Companion.LOG
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime

@Service(Service.Level.APP)
class AppLevelModelSyncService() : Disposable {

    companion object {
        fun getInstance(): AppLevelModelSyncService {
            return ApplicationManager.getApplication().getService(AppLevelModelSyncService::class.java)
        }
    }

    private val connections = LinkedHashMap<String, ServerConnection>()
    private val coroutinesScope = CoroutineScope(Dispatchers.Default)
    private val connectionCheckingJob = coroutinesScope.launchLoop(
        BackoffStrategy(
            initialDelay = 3_000,
            maxDelay = 10_000,
            factor = 1.2,
        ),
    ) {
        for (connection in synchronized(connections) { connections.values.toList() }) {
            connection.checkConnection()
        }
    }

    @Synchronized
    fun addConnection(url: String): ServerConnection {
        return synchronized(connections) { connections.getOrPut(url) { ServerConnection(url) } }
    }

    override fun dispose() {
        coroutinesScope.cancel("disposed")
    }

    class ServerConnection(val url: String) {
        private var client: ValueWithMutex<IModelClientV2?> = ValueWithMutex(null)
        private var connected: Boolean = false

        suspend fun getClient(): IModelClientV2 {
            return client.getValue() ?: client.updateValue {
                it ?: ModelClientV2.builder().url(url).build().also { it.init() }
            }
        }

        suspend fun checkConnection() {
            try {
                getClient().getServerId()
                connected = true
            } catch (ex: Throwable) {
                connected = false
            }
        }
    }
}

@Service(Service.Level.PROJECT)
class ModelSyncService(val project: Project) : IModelSyncService, Disposable {
    private val mpsProject: MPSProject get() = ProjectHelper.fromIdeaProject(project)!!

    private val bindings = ArrayList<Binding>()
    private val coroutinesScope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    override fun addServer(url: String): IServerConnection {
        return AppLevelModelSyncService.getInstance().addConnection(url).let { Connection(it) }
    }

    @Synchronized
    override fun getServerConnections(): List<IServerConnection> {
        TODO("Not yet implemented")
    }

    @Synchronized
    override fun dispose() {
        bindings.forEach { it.deactivate() }
        coroutinesScope.cancel("disposed")
    }

    inner class Connection(val connection: AppLevelModelSyncService.ServerConnection) : IServerConnection {
        override fun activate() {
            TODO("Not yet implemented")
        }

        override fun deactivate() {
            TODO("Not yet implemented")
        }

        override fun remove() {
            TODO("Not yet implemented")
        }

        override fun getStatus(): IServerConnection.Status {
            TODO("Not yet implemented")
        }

        override suspend fun pullVersion(branchRef: BranchReference): IVersion {
            return connection.getClient().pull(branchRef, null)
        }

        override fun bind(branchRef: BranchReference, lastSyncedVersionHash: String?): IBinding {
            val binding = Binding(
                coroutinesScope = coroutinesScope,
                mpsProject = mpsProject,
                client = { connection.getClient() },
                branchRef = branchRef,
                initialVersionHash = lastSyncedVersionHash,
            )
            bindings.add(binding)
            binding.activate()
            return binding
        }

        override fun getBindings(): List<IBinding> {
            TODO("Not yet implemented")
        }
    }
}

class Binding(
    val coroutinesScope: CoroutineScope,
    override val mpsProject: org.jetbrains.mps.openapi.project.Project,
    val client: suspend () -> IModelClientV2,
    override val branchRef: BranchReference,
    val initialVersionHash: String?,
) : IBinding {
    companion object {
        val LOG = mu.KotlinLogging.logger { }
    }

    private val activated = AtomicBoolean(false)
    private val lastSyncedVersion = ValueWithMutex<IVersion?>(null)
    private var syncJob: Job? = null
    private var syncToServerTask: ValidatingJob? = null
    private var invalidatingListener: MyInvalidatingListener? = null

    private val repository: SRepository get() = mpsProject.repository

    override fun activate() {
        if (activated.getAndSet(true)) return
        syncJob = coroutinesScope.launch { syncJob() }
    }

    override fun deactivate() {
        if (!activated.getAndSet(false)) return

        syncJob?.cancel()
        syncJob = null
        syncToServerTask = null
        invalidatingListener?.stop()
        invalidatingListener = null
    }

    private suspend fun checkInSync(): String? {
        check(activated.get()) { "Binding is deactivated" }
        val version = lastSyncedVersion.flush()?.getOrThrow()
        if (version == null) return "Initial sync isn't done yet"
        if (invalidatingListener == null) return "No change listener registered in MPS"
        if (invalidatingListener?.hasAnyInvalidations() != false) return "There are pending changes in MPS"
        val remoteVersion = client().pullHash(branchRef)
        if (remoteVersion != version.getContentHash()) return "Local version (${version.getContentHash()} differs from remote version ($remoteVersion)"
        return null
    }

    override suspend fun flush(): IVersion {
        check(syncJob?.isActive == true) { "Synchronization is not active" }
        var reason = checkInSync()
        var i = 0
        while (reason != null) {
            i++
            if (i % 10 == 0) LOG.debug { "Still waiting for the synchronization to finish: $reason" }
            delay(100)
            reason = checkInSync()
        }
        return lastSyncedVersion.getValue()!!
    }

    private suspend fun CoroutineScope.syncJob() {
        // initial sync
        initialSync()

        // continuous sync to MPS
        launchLoop {
            val newHash = client().pollHash(branchRef, lastSyncedVersion.getValue())
            if (newHash != lastSyncedVersion.getValue()?.getContentHash()) {
                LOG.debug { "New remote version detected: $newHash" }
                syncToMPS()
            }
        }

        // continuous sync to server
        syncToServerTask = launchValidation {
            syncToServer()
        }
    }

    suspend fun ensureInitialized() {
        if (lastSyncedVersion.getValue() == null) {
            initialSync()
        }
    }

    private suspend fun initialSync() {
        lastSyncedVersion.updateValue { oldVersion ->
            LOG.debug { "Running initial synchronization" }

            val baseVersion = oldVersion
                ?: initialVersionHash?.let { client().loadVersion(branchRef.repositoryId, it, null) }
            if (baseVersion == null) {
                // Binding was never activated before. Overwrite local changes or do initial upload.

                val remoteVersion = client().pullIfExists(branchRef)
                if (remoteVersion == null) {
                    LOG.debug { "Repository don't exist. Will copy the local project to the server." }
                    // repository doesn't exist -> copy the local project to the server
                    val emptyVersion = client().initRepository(branchRef.repositoryId)
                    doSyncToServer(emptyVersion) ?: emptyVersion
                } else {
                    LOG.debug { "Repository exists. Will checkout version $remoteVersion" }
                    doSyncToMPS(null, remoteVersion)
                    remoteVersion
                }
            } else {
                // Binding was activated before. Preserve local changes.

                // push local changes that happened while the binding was deactivated
                val localChanges = doSyncFromMPS(baseVersion)
                val remoteVersion = if (localChanges != null) {
                    val mergedVersion = client().push(branchRef, localChanges, baseVersion)
                    doSyncToMPS(baseVersion, mergedVersion)
                    mergedVersion
                } else {
                    client().pull(branchRef, baseVersion)
                }

                // load remote changes into MPS
                doSyncToMPS(baseVersion, remoteVersion)

                remoteVersion
            }
        }
    }

    suspend fun syncToMPS(): IVersion {
        return lastSyncedVersion.updateValue { oldVersion ->
            client().pull(branchRef, oldVersion).also { newVersion ->
                doSyncToMPS(oldVersion, newVersion)
            }
        }
    }

    suspend fun syncToServer(): IVersion? {
        return lastSyncedVersion.updateValue { oldVersion ->
            if (oldVersion == null) {
                // have to wait for initial sync
                oldVersion
            } else {
                val newVersion = doSyncToServer(oldVersion)
                newVersion ?: oldVersion
            }
        }
    }

    private suspend fun doSyncToMPS(oldVersion: IVersion?, newVersion: IVersion) {
        if (oldVersion?.getContentHash() == newVersion.getContentHash()) return

        LOG.debug { "Updating MPS project from $oldVersion to $newVersion" }

        val mpsProjects = listOf(mpsProject as MPSProject)
        val baseVersion = oldVersion
        val filter = if (baseVersion != null) {
            val invalidationTree = InvalidationTree(100_000)
            val newTree = newVersion.getTree()
            newTree.visitChanges(
                baseVersion.getTree(),
                InvalidatingVisitor(newTree, invalidationTree),
            )
            invalidationTree
        } else {
            FullSyncFilter()
        }

        val targetRoot = MPSRepositoryAsNode(repository)
        writeToMPS {
            if (invalidatingListener?.hasAnyInvalidations() == true) {
                // Concurrent modification!
                // Write changes from MPS to a new version first and try again after it is merged.
                LOG.debug { "Skipping sync to MPS because there are pending changes in MPS" }
                return@writeToMPS
            }

            getMPSListener().runSync {
                val branch = TreePointer(newVersion.getTree())
                val nodeAssociation = NodeAssociationFromModelServer(branch, targetRoot.getModel())
                ModelSynchronizer(
                    filter = filter,
                    sourceRoot = branch.getRootNode().asWritableNode(),
                    targetRoot = targetRoot,
                    nodeAssociation = nodeAssociation,
                    sourceMask = MPSProjectSyncMask(mpsProjects, false),
                    targetMask = MPSProjectSyncMask(mpsProjects, true),
                ).synchronize()
            }
        }
    }

    private suspend fun <R> writeToMPS(body: () -> R): R {
        val result = ArrayList<R>()
        ApplicationManager.getApplication().invokeAndWait({
            ApplicationManager.getApplication().runWriteAction {
                repository.modelAccess.executeUndoTransparentCommand {
                    ModelixMpsApi.runWithProject(mpsProject) {
                        result += body()
                    }
                }
            }
        }, ModalityState.NON_MODAL)
//        withContext(Dispatchers.Main) {
//        }
        return result.single()
    }

    private fun getMPSListener() = invalidatingListener ?: initializeListener()

    private fun initializeListener(): MyInvalidatingListener {
        // Being inside a transaction ensure there are not writes, and we don't lose changes.
        repository.modelAccess.checkReadAccess()
        check(invalidatingListener == null)
        return MyInvalidatingListener().also {
            invalidatingListener = it
            it.start(repository)
        }
    }

    /**
     * @return null if nothing changed
     */
    private suspend fun doSyncFromMPS(oldVersion: IVersion): IVersion? {
        check(lastSyncedVersion.isLocked())

        LOG.debug { "Commiting MPS changes" }

        val mpsProjects = listOf(mpsProject as MPSProject)
        val client = client()
        val newVersion = repository.computeRead {
            fun sync(invalidationTree: IIncrementalUpdateInformation): IVersion? {
                return oldVersion.runWrite(client) { branch ->
                    ModelixMpsApi.runWithProject(mpsProject) {
                        ModelSynchronizer(
                            filter = invalidationTree,
                            sourceRoot = MPSRepositoryAsNode(ModelixMpsApi.getRepository()),
                            targetRoot = branch.getRootNode().asWritableNode(),
                            nodeAssociation = NodeAssociationToModelServer(branch),
                            sourceMask = MPSProjectSyncMask(mpsProjects, true),
                            targetMask = MPSProjectSyncMask(mpsProjects, false),
                        ).synchronize()
                    }
                }
            }

            if (invalidatingListener == null) {
                sync(FullSyncFilter()).also {
                    // registering the listener after the sync is sufficient
                    // because we are in a read action that prevents model changes
                    initializeListener()
                }
            } else {
                invalidatingListener!!.runSync { sync(it) }
            }
        }

        LOG.debug { if (newVersion == null) "Nothing changed" else "New version created: $newVersion" }

        return newVersion
    }

    /**
     * @return null if nothing changed
     */
    private suspend fun doSyncToServer(oldVersion: IVersion): IVersion? {
        return doSyncFromMPS(oldVersion)?.let { client().push(branchRef, it, oldVersion) }
    }

    private inner class MyInvalidatingListener : MPSInvalidatingListener(repository) {
        override fun onInvalidation() {
            syncToServerTask?.invalidate()
        }
    }
}

fun CoroutineScope.launchLoop(body: suspend () -> Unit) = launchLoop(BackoffStrategy(), body)
fun CoroutineScope.launchLoop(backoffStrategy: BackoffStrategy, body: suspend () -> Unit) = launch { jobLoop(backoffStrategy, body) }

suspend fun jobLoop(body: suspend () -> Unit): Unit = jobLoop(BackoffStrategy(), body)

suspend fun jobLoop(
    backoffStrategy: BackoffStrategy,
    body: suspend () -> Unit,
) {
    while (true) {
        try {
            backoffStrategy.wait()
            body()
            backoffStrategy.success()
        } catch (ex: CancellationException) {
            break
        } catch (ex: Throwable) {
            LOG.warn("Exception during synchronization", ex)
            backoffStrategy.failed()
        }
    }
}

class BackoffStrategy(
    val initialDelay: Long = 500,
    val maxDelay: Long = 10_000,
    val factor: Double = 1.5,
) {
    var currentDelay: Long = initialDelay

    fun failed() {
        currentDelay = (currentDelay * factor).roundToLong().coerceAtMost(maxDelay)
    }

    fun success() {
        currentDelay = initialDelay
    }

    suspend fun wait() {
        delay(currentDelay)
    }
}

class ValueWithMutex<E>(private var value: E) {
    private val mutex = Mutex()
    private var lastUpdateResult: Result<E>? = null

    suspend fun <R : E> updateValue(body: suspend (E) -> R): R {
        return mutex.withLock {
            val newValue = runCatching {
                body(value)
            }
            lastUpdateResult = newValue
            newValue.onFailure {
                LOG.error(it) { "Value update failed. Keeping $value" }
            }
            newValue.getOrThrow().also { value = it }
        }
    }

    /**
     * Blocks until any active update is done.
     * @return The result of the most recent update attempt.
     */
    suspend fun flush(): Result<E>? {
        return mutex.withLock { lastUpdateResult }
    }

    fun isLocked() = mutex.isLocked

    fun getValue(): E = value
}
