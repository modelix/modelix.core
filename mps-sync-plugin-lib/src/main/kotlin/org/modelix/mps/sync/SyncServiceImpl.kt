package org.modelix.mps.sync

import com.intellij.openapi.project.Project
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.bindings.ModuleBinding
import org.modelix.mps.sync.modelix.ModelixBranchListener
import org.modelix.mps.sync.modelix.ReplicatedModelRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.RepositoryChangeListener
import org.modelix.mps.sync.tasks.FuturesWaitQueue
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.modelixToMps.initial.ITreeToSTreeTransformer
import java.net.ConnectException
import java.net.URL
import java.util.concurrent.CompletableFuture

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SyncServiceImpl : SyncService {

    private val logger = KotlinLogging.logger {}
    private val mpsProjectInjector = ActiveMpsProjectInjector

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    val activeClients = mutableSetOf<ModelClientV2>()
    private val replicatedModelByBranchReference = mutableMapOf<BranchReference, ReplicatedModel>()
    private val changeListenerByReplicatedModel = mutableMapOf<ReplicatedModel, IBranchListener>()

    private var projectWithChangeListener: Pair<MPSProject, RepositoryChangeListener>? = null

    init {
        logger.info { "============================================ Registering builtin languages" }
        // just a dummy call, the initializer of ILanguageRegistry takes care of the rest...
        ILanguageRepository.default.javaClass
    }

    // todo add afterActivate to allow async refresh
    override suspend fun connectModelServer(
        serverURL: URL,
        jwt: String,
        callback: (() -> Unit)?,
    ): ModelClientV2 {
        // avoid reconnect to existing server
        val client = activeClients.find { it.baseUrl == serverURL.toString() }
        client?.let {
            logger.info { "Using already existing connection to $serverURL" }
            return it
        }

        // TODO: use JWT here
        val modelClientV2: ModelClientV2 = ModelClientV2.builder().url(serverURL.toString()).build()

        runBlocking(coroutineScope.coroutineContext) {
            try {
                logger.info { "Connecting to $serverURL" }
                modelClientV2.init()
            } catch (e: ConnectException) {
                logger.warn { "Unable to connect: ${e.message} / ${e.cause}" }
                throw e
            }
        }
        logger.info { "Connection to $serverURL successful" }
        activeClients.add(modelClientV2)

        callback?.invoke()

        return modelClientV2
    }

    override fun disconnectModelServer(
        client: ModelClientV2,
        callback: (() -> Unit)?,
    ) {
        // TODO what shall happen with the bindings if we disconnect from model server?
        activeClients.remove(client)
        client.close()
        callback?.invoke()
    }

    override suspend fun bindModule(
        client: ModelClientV2,
        branchReference: BranchReference,
        module: INode,
        callback: (() -> Unit)?,
    ): Iterable<IBinding> {
        if (replicatedModelByBranchReference.containsKey(branchReference)) {
            return emptyList()
        }

        // set up a client, a replicated model and an implementation of a binding (to MPS)
        val bindings = runBlocking(coroutineScope.coroutineContext) {
            // TODO how to handle multiple replicated models at the same time?
            val replicatedModel = client.getReplicatedModel(branchReference)
            /*
             * TODO fixme:
             * (1) How to propagate replicated model to other places of code?
             * (2) How to know to which replicated model we want to upload? (E.g. when connecting to multiple model servers?)
             * (3) How to replace the outdated replicated models that are already used from the registry?
             *
             * Possible answers:
             * (1) via the registry
             * (2) Base the selection on the parent project and the active model server connections we have. E.g. let the user select to which model server they want to upload the changes and so they get the corresponding replicated model.
             * (3) We don't. We have to make sure that the places always have the latest replicated models from the registry. E.g. if we disconnect from the model server then we remove the replicated model (and thus break the registered event handlers), otherwise the event handlers as for the replicated model from the registry (based on some identifying metainfo for example, so to know which replicated model they need).
             */
            ReplicatedModelRegistry.model = replicatedModel
            replicatedModelByBranchReference[branchReference] = replicatedModel

            // TODO when and how to dispose the replicated model and everything that depends on it?
            val branch = replicatedModel.start()

            val targetProject = mpsProjectInjector.activeMpsProject!!
            val languageRepository = registerLanguages(targetProject)

            // transform the model
            val bindings = ITreeToSTreeTransformer(branch, languageRepository).transform(module)

            // register replicated model change listener
            // TODO branch probably not needed
            val listener = ModelixBranchListener(replicatedModel, languageRepository, branch)
            branch.addListener(listener)
            changeListenerByReplicatedModel[replicatedModel] = listener

            // register MPS project change listener
            if (projectWithChangeListener == null) {
                val repositoryChangeListener = RepositoryChangeListener(branch)
                targetProject.repository.addRepositoryListener(repositoryChangeListener)
                projectWithChangeListener = Pair(targetProject, repositoryChangeListener)
            }

            bindings
        }

        // trigger callback after activation
        callback?.invoke()

        return bindings
    }

    // This will already activate the bindings (different to bindModule) and therefore not return the list of bindings
    // TODO almost duplicated code with bindModule. Maybe we can merge the code and only have one method
    /*TODO override*/ suspend fun rebindModue(
        client: ModelClientV2,
        branchReference: BranchReference,
        module: SModule,
        lastKnownVersion: CLVersion,
        callback: (() -> Unit)? = null,
    ): Iterable<IBinding> {
        if (replicatedModelByBranchReference.containsKey(branchReference)) { return emptyList() }

        val futureBindings = CompletableFuture<Iterable<IBinding>>()
        // set up a client, a replicated model and an implementation of a binding (to MPS)
        runBlocking(coroutineScope.coroutineContext) {
            // TODO how to handle multiple replicated models at the same time?
            val replicatedModel = client.getReplicatedModel(branchReference)
            /*
             * TODO fixme:
             * (1) How to propagate replicated model to other places of code?
             * (2) How to know to which replicated model we want to upload? (E.g. when connecting to multiple model servers?)
             * (3) How to replace the outdated replicated models that are already used from the registry?
             *
             * Possible answers:
             * (1) via the registry
             * (2) Base the selection on the parent project and the active model server connections we have. E.g. let the user select to which model server they want to upload the changes and so they get the corresponding replicated model.
             * (3) We don't. We have to make sure that the places always have the latest replicated models from the registry. E.g. if we disconnect from the model server then we remove the replicated model (and thus break the registered event handlers), otherwise the event handlers as for the replicated model from the registry (based on some identifying metainfo for example, so to know which replicated model they need).
             */
            ReplicatedModelRegistry.model = replicatedModel
            replicatedModelByBranchReference[branchReference] = replicatedModel

            val targetProject = mpsProjectInjector.activeMpsProject!!
            val languageRepository = registerLanguages(targetProject)

            // TODO when and how to dispose the replicated model and everything that depends on it?
            replicatedModel.start(lastKnownVersion) { branch ->
                // register replicated model change listener
                // TODO branch or replicatedModel probably not needed
                val listener = ModelixBranchListener(replicatedModel, languageRepository, branch)
                branch.addListener(listener)
                changeListenerByReplicatedModel[replicatedModel] = listener

                // register MPS project change listener
                if (projectWithChangeListener == null) {
                    val repositoryChangeListener = RepositoryChangeListener(branch)
                    targetProject.repository.addRepositoryListener(repositoryChangeListener)
                    projectWithChangeListener = Pair(targetProject, repositoryChangeListener)
                }

                // create bindings
                val moduleBinding = ModuleBinding(module as AbstractModule, branch)
                val bindings: MutableList<IBinding> = mutableListOf(moduleBinding)
                BindingsRegistry.addModuleBinding(moduleBinding)
                ActiveMpsProjectInjector.activeMpsProject!!.modelAccess.runReadAction {
                    for (model in module.models) {
                        val modelBinding = ModelBinding(model as SModelBase, branch)
                        bindings.add(modelBinding)
                        BindingsRegistry.addModelBinding(modelBinding)
                    }
                }

                futureBindings.complete(bindings)
            }
        }
        val bindings = withContext(Dispatchers.IO) {
            futureBindings.get()
        }
        callback?.invoke()
        return bindings
    }

    override fun setActiveProject(project: Project) {
        mpsProjectInjector.setActiveProject(project)
    }

    override fun dispose() {
        // cancel all running coroutines
        coroutineScope.cancel()
        SyncQueue.close()
        FuturesWaitQueue.close()
        // unregister change listeners
        resetProjectWithChangeListener()
        changeListenerByReplicatedModel.forEach { it.key.getBranch().removeListener(it.value) }
        // dispose the clients
        activeClients.forEach { it.close() }
        // dispose all bindings
        BindingsRegistry.getModuleBindings().forEach { it.deactivate(removeFromServer = false) }
        BindingsRegistry.getModelBindings().forEach { it.deactivate(removeFromServer = false) }
    }

    private fun registerLanguages(project: MPSProject): MPSLanguageRepository {
        val repository = project.repository
        val mpsLanguageRepo = MPSLanguageRepository(repository)
        ILanguageRepository.register(mpsLanguageRepo)
        return mpsLanguageRepo
    }

    private fun resetProjectWithChangeListener() {
        projectWithChangeListener?.let {
            val project = it.first
            val listener = it.second
            project.repository.removeRepositoryListener(listener)
            projectWithChangeListener = null
        }
    }

    fun get_ReplicatedModels(): Collection<ReplicatedModel> {
        return replicatedModelByBranchReference.values
    }
}
