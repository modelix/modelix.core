package org.modelix.model

import kotlinx.datetime.Clock
import org.modelix.model.api.*
import org.modelix.model.client.IModelClient
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.*
import org.modelix.model.metameta.MetaModelBranch
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.CPVersion
import org.modelix.model.persistent.MapBaseStore

object ModelFacade {

    fun newLocalTree(): ITree {
        return CLTree(ObjectStoreCache(MapBaseStore()))
    }

    fun getRootNode(branch: IBranch): INode {
        return PNodeAdapter(ITree.ROOT_ID, branch)
    }

//    fun connectToServer(url: String): IModelClient {
//        return RestWebModelClient(url)
//    }

    fun loadCurrentVersion(client: IModelClient, branch: BranchReference): CLVersion? {
        // require(client is RestWebModelClient)
        val versionHash = client.get(branch.getKey()) ?: return null
        return CLVersion.loadFromHash(versionHash, client.storeCache)
    }

    fun loadCurrentModel(client: IModelClient, branch: BranchReference): ITree? {
        return loadCurrentVersion(client, branch)?.tree
    }

    fun toLocalBranch(tree: ITree): IBranch = TreePointer(tree, IdGenerator.getInstance(1))

    fun toNode(tree: ITree): INode {
        return PNodeAdapter(ITree.ROOT_ID, toLocalBranch(tree))
    }

    fun readNode(node: INode, body: () -> Unit) {
        node.getArea().executeRead(body)
    }

    fun writeNode(node: INode, body: () -> Unit) {
        node.getArea().executeWrite(body)
    }

    fun createBranchReference(repositoryId: RepositoryId, branchName: String? = null): BranchReference {
        return repositoryId.getBranchReference(branchName)
    }

    fun createBranchReference(repositoryId: String, branchName: String? = null): BranchReference {
        return createBranchReference(RepositoryId(repositoryId), branchName)
    }

    fun mergeUpdate(client: IModelClient, branch: BranchReference, baseVersionHash: String? = null, userName: String?, body: (IWriteTransaction) -> Unit): CLVersion {
        val baseVersionHash: String = baseVersionHash
            ?: client.get(branch.getKey())
            ?: throw RuntimeException("$branch doesn't exist")
        val baseVersionData: CPVersion = client.storeCache.get(baseVersionHash, { CPVersion.deserialize(it) })
            ?: throw RuntimeException("version not found: $baseVersionHash")
        val baseVersion = CLVersion(baseVersionData, client.storeCache)
        return applyUpdate(client, baseVersion, branch, userName, body)
    }

    private fun applyUpdate(
        client: IModelClient,
        baseVersion: CLVersion,
        branch: BranchReference,
        userId: String?,
        body: (IWriteTransaction) -> Unit
    ): CLVersion {
        val otBranch = OTBranch(PBranch(baseVersion.tree, client.idGenerator), client.idGenerator, client.storeCache)
        MetaModelBranch(otBranch).computeWriteT { t ->
            body(t)
        }

        val operationsAndTree = otBranch.operationsAndTree
        val newVersion = CLVersion.createRegularVersion(
            client.idGenerator.generate(),
            Clock.System.now().epochSeconds.toString(),
            userId,
            operationsAndTree.second as CLTree,
            baseVersion,
            operationsAndTree.first.map { it.getOriginalOp() }.toTypedArray()
        )
        val currentVersion = loadCurrentVersion(client, branch)
            ?: throw RuntimeException("$branch doesn't exist")
        val mergedVersion = VersionMerger(client.storeCache, client.idGenerator)
            .mergeChange(currentVersion, newVersion)
        client.asyncStore.put(branch.getKey(), mergedVersion.hash)
        // TODO handle concurrent write to the branchKey, otherwise versions might get lost. See ReplicatedRepository.
        return mergedVersion
    }
}
