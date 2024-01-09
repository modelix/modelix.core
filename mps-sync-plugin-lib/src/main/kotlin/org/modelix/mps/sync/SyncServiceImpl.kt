package org.modelix.mps.sync

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.transformation.modelixToMps.initial.ITreeToSTreeTransformer
import org.modelix.mps.sync.util.SyncBarrier
import java.net.ConnectException
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SyncServiceImpl : SyncService {

    private val logger: Logger = logger<SyncServiceImpl>()

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    val activeClients = mutableSetOf<ModelClientV2>()

    private lateinit var replicatedModel: ReplicatedModel

    init {
        logger.info("============================================ Registering builtin languages")
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
            logger.info("Using already existing connection to $serverURL")
            return it
        }

        // TODO: use JWT here
        val modelClientV2: ModelClientV2 = ModelClientV2.builder().url(serverURL.toString()).build()

        runBlocking(coroutineScope.coroutineContext) {
            try {
                logger.info("Connecting to $serverURL")
                modelClientV2.init()
            } catch (e: ConnectException) {
                logger.warn("Unable to connect: ${e.message} / ${e.cause}")
                throw e
            }
        }
        logger.info("Connection to $serverURL successful")
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

    override suspend fun bindModel(
        client: ModelClientV2,
        branchReference: BranchReference,
        model: INode,
        callback: (() -> Unit)?,
    ): IBinding {
        // set up a client, a replicated model and an implementation of a binding (to MPS)
        runBlocking(coroutineScope.coroutineContext) {
            // TODO how to handle multiple replicated models at the same time?
            replicatedModel = client.getReplicatedModel(branchReference)
            // TODO when and how to dispose the replicated model and everything that depends on it?
            replicatedModel.start()

            /**
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
            ReplicatedModelRegistry.instance.model = replicatedModel

            val isSynchronizing = SyncBarrier.instance
            val nodeMap = MpsToModelixMap.instance

            val targetProject = ActiveMpsProjectInjector.activeProject!!
            val languageRepository = registerLanguages(targetProject)

            // transform the model
            ITreeToSTreeTransformer(
                replicatedModel.getBranch(),
                targetProject,
                languageRepository,
                isSynchronizing,
                nodeMap,
                BindingsRegistry.instance,
            ).transform(model)

            // register change listener on replicated model
            // TODO fixme avoid the double-registration of the same listener...
            registerReplicatedModelChangeListener(
                replicatedModel,
                targetProject,
                languageRepository,
                isSynchronizing,
                nodeMap,
            )
        }

        // trigger callback after activation
        callback?.invoke()

        // TODO fixme we have to return a binding / bunch of bindings...
        return object : IBinding {
            override fun activate(callback: Runnable?) {
                TODO("Not yet implemented")
            }

            override fun deactivate(callback: Runnable?) {
                TODO("Not yet implemented")
            }
        }
    }

    override fun setActiveMpsProject(mpsProject: MPSProject) {
        ActiveMpsProjectInjector.activeProject = mpsProject
    }

    override fun getModelBindings() = BindingsRegistry.instance.getModelBindings()

    override fun getModuleBindings() = BindingsRegistry.instance.getModuleBindings()

    override fun dispose() {
        // cancel all running coroutines
        coroutineScope.cancel()
        // dispose the clients
        activeClients.forEach { it.close() }
        // dispose all bindings
        val bindingsRegistry = BindingsRegistry.instance
        bindingsRegistry.getModuleBindings().forEach { it.deactivate() }
        bindingsRegistry.getModelBindings().forEach { it.deactivate() }
    }

    private fun registerLanguages(project: MPSProject): MPSLanguageRepository {
        val repository = project.repository
        val mpsLanguageRepo = MPSLanguageRepository(repository)
        ILanguageRepository.register(mpsLanguageRepo)
        return mpsLanguageRepo
    }

    private fun registerReplicatedModelChangeListener(
        replicatedModel: ReplicatedModel,
        project: MPSProject,
        languageRepository: MPSLanguageRepository,
        isSynchronizing: SyncBarrier,
        nodeMap: MpsToModelixMap,
    ) {
        val listener = ModelixBranchListener(replicatedModel, project, languageRepository, isSynchronizing, nodeMap)
        replicatedModel.getBranch().addListener(listener)
    }
}
