package org.modelix.mps.sync

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.getNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.transformation.modelixToMps.incremental.TreeChangeVisitor
import org.modelix.mps.sync.transformation.modelixToMps.initial.ITreeToSTreeTransformer
import org.modelix.mps.sync.util.SyncBarrier
import java.net.ConnectException
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SyncServiceImpl : SyncService {

    private var log: Logger = logger<SyncServiceImpl>()

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    var clientBindingMap: MutableMap<ModelClientV2, MutableList<BindingImpl>> = mutableMapOf()

    private lateinit var replicatedModel: ReplicatedModel

    // todo add afterActivate to allow async refresh
    suspend fun connectToModelServer(
        serverURL: URL,
        jwt: String,
    ): ModelClientV2 {
        // avoid reconnect to existing server
        val client = clientBindingMap.keys.find { it.baseUrl == serverURL.toString() }
        client?.let {
            log.info("Using already existing connection to $serverURL")
            return it
        }

        // TODO: use JWT here
        val modelClientV2: ModelClientV2 = ModelClientV2.builder().url(serverURL.toString()).build()

        runBlocking(coroutineScope.coroutineContext) {
            try {
                log.info("Connecting to $serverURL")
                modelClientV2.init()
            } catch (e: ConnectException) {
                log.warn("Unable to connect: ${e.message} / ${e.cause}")
                throw e
            }
        }
        log.info("Connection to $serverURL successful")
        clientBindingMap[modelClientV2] = mutableListOf()
        return modelClientV2
    }

    fun disconnectModelServer(client: ModelClientV2) {
        clientBindingMap[client]?.forEach { it.deactivate() }
        clientBindingMap.remove(client)
        client.close()
    }

    override suspend fun bindModel(
        client: ModelClientV2,
        branchReference: BranchReference,
        model: INode,
        targetProject: MPSProject,
        languageRepository: MPSLanguageRepository,
        afterActivate: (() -> Unit)?,
    ): IBinding {
        lateinit var bindingImpl: BindingImpl

        // set up a client, a replicated model and an implementation of a binding (to MPS)
        runBlocking(coroutineScope.coroutineContext) {
            replicatedModel = client.getReplicatedModel(branchReference)
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
            // transform the model
            ITreeToSTreeTransformer(
                replicatedModel.getBranch(),
                targetProject,
                languageRepository,
                isSynchronizing,
                nodeMap,
            )
                .transform(model)
            bindingImpl = BindingImpl(replicatedModel, targetProject, languageRepository, isSynchronizing, nodeMap)
        }
        // trigger callback after activation
        afterActivate?.invoke()

        // remember the new binding
        clientBindingMap[client]!!.add(bindingImpl)

        return bindingImpl
    }

    fun dispose() {
        // cancel all running coroutines
        coroutineScope.cancel()
        // dispose the bindings
        clientBindingMap.values.forEach { it: MutableList<BindingImpl> -> it.forEach { it2 -> it2.deactivate() } }
        // dispose the clients
        clientBindingMap.keys.forEach { it: ModelClientV2 -> it.close() }
    }

    fun removeServerConnectionAndAllBindings(serverURL: URL) {
        val foundClient = clientBindingMap.keys.find { it.baseUrl.equals(serverURL) } ?: return

        // TODO do a proper binding removal here?
        clientBindingMap[foundClient]!!.forEach { it.deactivate() }

        foundClient.close()

        clientBindingMap.remove(foundClient)
    }

    @Deprecated("TODO: remove this method")
    override fun moveNode(nodeId: String) {
        val branch = replicatedModel.getBranch()

        /*
        // move scheduling child from one Lecture to another
        val nodeId = 17179869198L
        val childNode = branch.getNode(nodeId)
        var childLink: IChildLink? = null
        branch.runReadT {
            childLink = childNode.getContainmentLink()
        }

        // delete old schedule
        val parentNode = branch.getNode(17179869190)
        branch.runWrite {
            parentNode.getChildren(childLink!!).forEach { it.remove() }
        }

        // add new schedule
        val parentNodeId = parentNode.nodeIdAsLong()
        branch.runWriteT { transaction ->
            transaction.moveChild(parentNodeId, childLink?.getSimpleName(), -1, nodeId)
        }
         */

        /*
        // move a LectureList from model root to a child node
        val lectureListToMoveNodeId = nodeId.toLong()
        val newParentNodeId = 17179869190
        branch.runWriteT { transaction ->
            transaction.moveChild(newParentNodeId, "sublecturelist", -1, lectureListToMoveNodeId)
        }
         */

        /*
        // move a LectureList from childNode to model root
        val lectureListToMoveNodeId = nodeId.toLong()
        val modelNodeId = 17179869185
        branch.runWriteT { transaction ->
            transaction.moveChild(modelNodeId, null, -1, lectureListToMoveNodeId)
        }
         */

        /*
        // delete model
        branch.runWrite{
            branch.getNode(17179869185).remove()
        }
         */

        /*
        // delete module
        branch.runWrite {
            branch.getNode(4294967309).remove()
        }
         */

        /*
        // move LectureList (root node) to a new model
        val lectureListNodeId = 17179869188
        val newModelId = nodeId.toLong()
        branch.runWriteT { transaction ->
            transaction.moveChild(newModelId, null, -1, lectureListNodeId)
        }
         */

        /*
        // create new model in the cloud
        branch.runWriteT {
            val cloudModuleId = 4294967309L
            val cloudModule = branch.getNode(cloudModuleId)
            val cloudModel = cloudModule.addNewChild(
                BuiltinLanguages.MPSRepositoryConcepts.Module.models,
                -1,
                BuiltinLanguages.MPSRepositoryConcepts.Model,
            )

            cloudModel.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.Model.id,
                "r:ce161c54-ea76-40a6-a31d-9d7cd01fecf2",
            )
            cloudModel.setPropertyValue(
                BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name,
                "University.Schedule.modelserver.backend.hellooworld",
            )
        }
         */

        // create new module in the cloud
        branch.runWriteT {
            val rootNode = branch.getNode(1)
            val cloudModel = rootNode.addNewChild(
                "modules",
                -1,
                BuiltinLanguages.MPSRepositoryConcepts.Module,
            )

            cloudModel.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.Module.id,
                "a04444a1-c8a4-48e8-a940-32a70d0f9cfe",
            )
            cloudModel.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.Module.moduleVersion,
                "0",
            )
            cloudModel.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.Module.compileInMPS,
                "true",
            )
            cloudModel.setPropertyValue(
                BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name,
                "University.Schedule.modelserver.backend.box2",
            )
        }

        /*
        // model import from new model to old one
        branch.runWriteT {
            val newModelId = nodeId.toLong()
            val oldModelId = 17179869185

            val newModel = branch.getNode(newModelId)
            val modelImport = newModel.addNewChild(
                BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports,
                -1,
                BuiltinLanguages.MPSRepositoryConcepts.ModelReference,
            )

            val oldModel = branch.getNode(oldModelId)
            modelImport.setReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model, oldModel)
        }
         */

        /*
        // add language dependency
        branch.runWriteT {
            val modelId = nodeId.toLong()
            val model = branch.getNode(modelId)
            val cloudLanguageDependency =
                model.addNewChild(
                    BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages,
                    -1,
                    BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency,
                )

            // warning: might be fragile, because we synchronize the properties by hand
            cloudLanguageDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name,
                "University.Schedule",
            )

            cloudLanguageDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid,
                "96533389-8d4c-46f2-b150-8d89155f7fca",
            )

            cloudLanguageDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version,
                "0",
            )
        }
         */

        /*
        // add devkit dependency
        branch.runWriteT {
            val modelId = nodeId.toLong()
            val model = branch.getNode(modelId)
            val cloudLanguageDependency =
                model.addNewChild(
                    BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages,
                    -1,
                    BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency,
                )

            // warning: might be fragile, because we synchronize the properties by hand
            cloudLanguageDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name,
                "University.Schedule.Devkit",
            )

            cloudLanguageDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid,
                "3f0b14cf-38db-4a9e-ae9e-6c078c16c2da",
            )
        }
         */

        /*
        // add module dependency
        branch.runWriteT {
            val moduleId = nodeId.toLong()
            val module = branch.getNode(moduleId)
            val cloudDependency =
                module.addNewChild(
                    BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies,
                    -1,
                    BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency,
                )

            cloudDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.reexport,
                "false",
            )

            cloudDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.uuid,
                "a04444a1-c8a4-48e8-a940-32a70d0e8bfc",
            )

            cloudDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.name,
                "University.Schedule.modelserver.backend.sandbox",
            )
        }
         */
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class BindingImpl(
    val replicatedModel: ReplicatedModel,
    project: MPSProject,
    languageRepository: MPSLanguageRepository,
    isSynchronizing: SyncBarrier,
    nodeMap: MpsToModelixMap,
) : IBinding {

    init {
        replicatedModel.getBranch().addListener(object : IBranchListener {
            override fun treeChanged(oldTree: ITree?, newTree: ITree) {
                if (oldTree != null) {
                    newTree.visitChanges(
                        oldTree,
                        TreeChangeVisitor(replicatedModel, project, languageRepository, isSynchronizing, nodeMap),
                    )
                }
            }
        })
    }

    override fun activate(callback: Runnable?) {
        TODO("Not yet implemented")
    }

    override fun deactivate(callback: Runnable?) {
        // TODO unregister Model/Module/NodeChangeListeners?
        replicatedModel.dispose()
    }
}
