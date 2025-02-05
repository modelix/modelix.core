package org.modelix.mps.sync3

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import jetbrains.mps.project.MPSProject
import org.modelix.model.IVersion
import org.modelix.model.api.IBranch
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWrite
import org.modelix.model.client2.runWriteOnBranch
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.GlobalModelListener
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.operations.OTBranch
import org.modelix.model.sync.bulk.FullSyncFilter
import org.modelix.model.sync.bulk.InvalidatingVisitor
import org.modelix.model.sync.bulk.InvalidationTree
import org.modelix.model.sync.bulk.ModelSynchronizer
import org.modelix.model.sync.bulk.NodeAssociationFromModelServer
import org.modelix.model.sync.bulk.NodeAssociationToModelServer
import org.modelix.mps.api.ModelixMpsApi
import org.modelix.mps.model.sync.bulk.MPSProjectSyncMask

interface IModelSyncService {
    fun addModelServer(url: String): IModelServerConnection
    fun getModelServerConnections(): List<IModelServerConnection>

    interface IModelServerConnection {

    }
}

@Service(Service.Level.APP)
class ModelSyncService : Disposable {

    private val lastCheckout: CheckoutData? = null
    private val lastSyncedVersion: IVersion? = null
    private val client: IModelClientV2 = ModelClientV2.builder().url("").build()

    suspend fun runSyncToMPS(branchRef: BranchReference, newVersion: IVersion, mpsProjects: List<MPSProject>) {
        val invalidationTree = InvalidationTree(100_000)
        val targetRoot = MPSRepositoryAsNode(ModelixMpsApi.getRepository())
        val versionWithUpdatedAssociations = newVersion.runWrite(client) { branch ->
            val nodeAssociation = NodeAssociationFromModelServer(branch, targetRoot.getModel())
            ModelSynchronizer(
                filter = invalidationTree,
                sourceRoot = branch.getRootNode().asWritableNode(),
                targetRoot = targetRoot,
                nodeAssociation = nodeAssociation,
                sourceMask = MPSProjectSyncMask(mpsProjects, false),
                targetMask = MPSProjectSyncMask(mpsProjects, true)
            ).synchronize()
            nodeAssociation.writeAssociations()
        }
        if (versionWithUpdatedAssociations != null) {
            client.push(branchRef, versionWithUpdatedAssociations, newVersion)
        }
    }

    suspend fun runSyncToServer(branchRef: BranchReference, mpsProjects: List<MPSProject>) {
        val client: IModelClientV2 = ModelClientV2.builder().url("").build()
        val baseVersion = lastSyncedVersion ?: if (lastCheckout != null) {
            client.loadVersion(RepositoryId(lastCheckout.repositoryId), lastCheckout.versionHash, null)
        } else {
            client.pull(branchRef, null)
        }

        val newVersion = baseVersion.runWrite(client) { branch ->
            ModelSynchronizer(
                filter = InvalidationTree(),
                sourceRoot = MPSRepositoryAsNode(ModelixMpsApi.getRepository()),
                targetRoot = branch.getRootNode().asWritableNode(),
                nodeAssociation = NodeAssociationToModelServer(branch),
                sourceMask = MPSProjectSyncMask(mpsProjects, true),
                targetMask = MPSProjectSyncMask(mpsProjects, false)
            ).synchronize()
        }

        if (newVersion != null) {
            client.push(branchRef, newVersion, baseVersion)
        }
    }

    override fun dispose() {
        TODO("Not yet implemented")
    }
}

class Binding(
    val client: IModelClientV2,
    val branchRef: BranchReference,
) {
    private var lastSyncedVersion: IVersion? = null
    private val globalListener = GlobalModelListener()

    suspend fun setLastSyncedVersion(versionHash: String) {
        lastSyncedVersion = client.loadVersion(branchRef.repositoryId, versionHash, lastSyncedVersion)
    }

    suspend fun runSyncToMPS(branchRef: BranchReference, newVersion: IVersion, mpsProjects: List<MPSProject>) {
        val baseVersion = lastSyncedVersion
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

        val targetRoot = MPSRepositoryAsNode(ModelixMpsApi.getRepository())
        val versionWithUpdatedAssociations = newVersion.runWrite(client) { branch ->
            val nodeAssociation = NodeAssociationFromModelServer(branch, targetRoot.getModel())
            ModelSynchronizer(
                filter = filter,
                sourceRoot = branch.getRootNode().asWritableNode(),
                targetRoot = targetRoot,
                nodeAssociation = nodeAssociation,
                sourceMask = MPSProjectSyncMask(mpsProjects, false),
                targetMask = MPSProjectSyncMask(mpsProjects, true)
            ).synchronize()
            nodeAssociation.writeAssociations()
        }
        if (versionWithUpdatedAssociations != null) {
            client.push(branchRef, versionWithUpdatedAssociations, newVersion)
        }
    }

    suspend fun runSyncToServer(branchRef: BranchReference, mpsProjects: List<MPSProject>) {
        val baseVersion = lastSyncedVersion ?: if (lastCheckout != null) {
            client.loadVersion(RepositoryId(lastCheckout.repositoryId), lastCheckout.versionHash, null)
        } else {
            client.pull(branchRef, null)
        }

        val newVersion = baseVersion.runWrite(client) { branch ->
            ModelSynchronizer(
                filter = InvalidationTree(),
                sourceRoot = MPSRepositoryAsNode(ModelixMpsApi.getRepository()),
                targetRoot = branch.getRootNode().asWritableNode(),
                nodeAssociation = NodeAssociationToModelServer(branch),
                sourceMask = MPSProjectSyncMask(mpsProjects, true),
                targetMask = MPSProjectSyncMask(mpsProjects, false)
            ).synchronize()
        }

        if (newVersion != null) {
            client.push(branchRef, newVersion, baseVersion)
        }
    }
}


@Service(Service.Level.PROJECT)
class ModelSyncForMPSProject(private val project: Project) : Disposable {

    init {

    }

    override fun dispose() {

    }
}

class RepositoryBinding {

}
