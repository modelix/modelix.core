package org.modelix.mps.sync

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.transformation.MpsToModelixMap
import org.modelix.mps.sync.transformation.modelixToMps.incremental.TreeChangeVisitor
import org.modelix.mps.sync.transformation.modelixToMps.initial.ITreeToSTreeTransformer
import java.net.ConnectException
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

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

            val isSynchronizing = AtomicReference<Boolean>()
            val nodeMap = MpsToModelixMap()
            // transform the model
            ITreeToSTreeTransformer(replicatedModel, targetProject, languageRepository, isSynchronizing, nodeMap)
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

        // move LectureList (root node) to a new model
        val lectureListNodeId = 17179869188
        val newModelId = nodeId.toLong()
        branch.runWriteT { transaction ->
            transaction.moveChild(newModelId, null, -1, lectureListNodeId)
        }
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class BindingImpl(
    val replicatedModel: ReplicatedModel,
    project: MPSProject,
    languageRepository: MPSLanguageRepository,
    isSynchronizing: AtomicReference<Boolean>,
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
