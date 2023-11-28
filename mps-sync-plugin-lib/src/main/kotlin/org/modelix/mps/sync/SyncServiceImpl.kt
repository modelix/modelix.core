package org.modelix.mps.sync

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.extapi.module.SModuleBase
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.INode
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.PropertyFromName
import org.modelix.model.api.getNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.mps.sync.mps.util.runReadBlocking
import org.modelix.mps.sync.mps.util.runWriteActionInEDTBlocking
import org.modelix.mps.sync.transformation.ITreeToSTreeTransformer
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.util.nodeIdAsLong
import org.modelix.mps.sync.util.runIfAlone
import java.net.ConnectException
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SyncServiceImpl : SyncService {

    private var log: Logger = logger<SyncServiceImpl>()

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    public var clientBindingMap: MutableMap<ModelClientV2, MutableList<BindingImpl>> = mutableMapOf()

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
//                modelClientV2.initRepository(RepositoryId("lolwat"+(0..10).random().toString()))
            } catch (e: ConnectException) {
                log.warn("Unable to connect: ${e.message} / ${e.cause}")
                throw e
            }
        }
        log.info("Connection to $serverURL successful")
        clientBindingMap[modelClientV2] = mutableListOf<BindingImpl>()
        return modelClientV2
    }

    fun disconnectModelServer(client: ModelClientV2) {
        clientBindingMap[client]?.forEach { it.deactivate() }
        clientBindingMap.remove(client)
        client.close()
    }

    override suspend fun bindModel(
        modelClientV2: ModelClientV2,
        branchReference: BranchReference,
        modelName: String,
        model: INode,
        project: MPSProject,
        afterActivate: (() -> Unit)?,
    ): IBinding {
        lateinit var bindingImpl: BindingImpl

        // set up a client, a replicated model and an implementation of a binding (to MPS)
        runBlocking(coroutineScope.coroutineContext) {
            log.info("Binding model $modelName")
            val replicatedModel: ReplicatedModel = modelClientV2.getReplicatedModel(branchReference)
            replicatedModel.start()

            // 🚧🏗️👷👷‍♂️ WARNING Construction area 🚧🚧🚧
            val isSynchronizing = AtomicReference<Boolean>()
            val nodeMap = MpsToModelixMap()
            ITreeToSTreeTransformer(replicatedModel, project, isSynchronizing, nodeMap).transform(model)
            bindingImpl = BindingImpl(replicatedModel, project, isSynchronizing, nodeMap)
        }
        // trigger callback after activation
        afterActivate?.invoke()

        // remember the new binding
        clientBindingMap[modelClientV2]!!.add(bindingImpl)

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

    suspend fun removeServerConnectionAndAllBindings(serverURL: URL) {
        val foundClient = clientBindingMap.keys.find { it.baseUrl.equals(serverURL) } ?: return

        // TODO do a proper binding removal here?
        clientBindingMap[foundClient]!!.forEach { it.deactivate() }

        foundClient.close()

        clientBindingMap.remove(foundClient)
    }
}

@UnstableModelixFeature(reason = "The new mod elix MPS plugin is under construction", intendedFinalization = "2024.1")
class BindingImpl(
    val replicatedModel: ReplicatedModel,
    mpsProject: MPSProject,
    private val isSynchronizing: AtomicReference<Boolean>,
    private val nodeMap: MpsToModelixMap,
) : IBinding {

    private val branch: IBranch = replicatedModel.getBranch()
    private val modelAccess = mpsProject.modelAccess

    init {
        setupBinding()
    }

    private fun setupBinding() {
        // listen to changes on the branch in the replicatedModel (model-server side) ...
        // TODO refactor the IBranchListener to a new class
        branch.addListener(object : IBranchListener {
            fun handleThrowable(t: Throwable) {
                t.printStackTrace()
            }

            override fun treeChanged(oldTree: ITree?, newTree: ITree) {
                if (oldTree == null) {
                    return
                }
                newTree.visitChanges(
                    oldTree,
                    object : ITreeChangeVisitorEx {

                        fun getBranch() = replicatedModel.getBranch()

                        override fun referenceChanged(nodeId: Long, role: String) {
                            // TODO how to transform modelImport, modelDependency, languageDependency changes?
                            // TODO Are they also "referenceChanged" events?
                            isSynchronizing.runIfAlone(::handleThrowable) {
                                val sNode = nodeMap.getNode(nodeId)!!
                                var sReferenceLink: SReferenceLink? = null
                                modelAccess.runReadBlocking {
                                    sReferenceLink = sNode.concept.referenceLinks.find { it.name == role }
                                }

                                val iNode = getBranch().getNode(nodeId)
                                var iReferenceLink: IReferenceLink?
                                var targetINode: INode? = null
                                getBranch().runRead {
                                    iReferenceLink = iNode.getReferenceLinks().find { it.getSimpleName() == role }
                                    targetINode = iReferenceLink?.let { iNode.getReferenceTarget(it) }
                                }
                                val targetSNode = targetINode?.let { nodeMap.getNode(it.nodeIdAsLong()) }

                                modelAccess.runWriteActionInEDTBlocking {
                                    sNode.setReferenceTarget(sReferenceLink!!, targetSNode)
                                }
                            }
                        }

                        override fun propertyChanged(nodeId: Long, role: String) {
                            // TODO what if nodeId is a Model or a Module?
                            isSynchronizing.runIfAlone(::handleThrowable) {
                                val sNode = nodeMap.getNode(nodeId)!!
                                var sProperty: SProperty? = null
                                modelAccess.runReadBlocking {
                                    sProperty = sNode.concept.properties.find { it.name == role }
                                }

                                val iNode = getBranch().getNode(nodeId)
                                val iProperty = PropertyFromName(role)
                                var value: String? = null
                                getBranch().runRead { value = iNode.getPropertyValue(iProperty) }

                                modelAccess.runWriteActionInEDTBlocking {
                                    sNode.setProperty(sProperty!!, value)
                                }
                            }
                        }

                        override fun nodeRemoved(nodeId: Long) {
                            val sNode = nodeMap.getNode(nodeId)
                            sNode?.let {
                                modelAccess.runWriteActionInEDTBlocking {
                                    it.delete()
                                    nodeMap.remove(nodeId)
                                }
                            }

                            val sModel = nodeMap.getModel(nodeId)
                            sModel?.let {
                                val sModule = sModel.module
                                require(sModel is SModelBase) { "Model ${sModel.modelId} is not SModelBase" }
                                require(sModule is SModuleBase) { "Module ${sModule.moduleId} is not SModuleBase" }
                                sModule.unregisterModel(sModel)
                                nodeMap.remove(nodeId)
                            }

                            val sModule = nodeMap.getModule(nodeId)
                            sModule?.let {
                                require(sModule is SModuleBase) { "Module ${sModule.moduleId} is not SModuleBase" }
                                sModule.dispose()
                                nodeMap.remove(nodeId)
                            }
                        }

                        override fun nodeAdded(nodeId: Long) {
                            // TODO decide if node is model, module or model and transform using the corresponding transformation methods
                            println("node added $nodeId")
                        }

                        override fun childrenChanged(nodeId: Long, role: String?) {}

                        override fun containmentChanged(nodeId: Long) {}
                    },
                )
            }
        })
    }

    override fun deactivate(callback: Runnable?) {
        replicatedModel.dispose()
    }

    override fun activate(callback: Runnable?) {
        TODO("Not yet implemented")
    }
}
