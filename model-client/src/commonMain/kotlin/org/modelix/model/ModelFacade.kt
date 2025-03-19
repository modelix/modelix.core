package org.modelix.model

import kotlinx.datetime.Clock
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.PBranch
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.TreePointer
import org.modelix.model.api.deepUnwrapNode
import org.modelix.model.client.IModelClient
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.operations.OTBranch
import org.modelix.model.persistent.MapBasedStore
import kotlin.jvm.JvmOverloads

object ModelFacade {

    @JvmOverloads
    fun newLocalTree(useRoleIds: Boolean = true): ITree {
        return CLTree.builder(createObjectStoreCache(MapBasedStore())).useRoleIds(useRoleIds).build()
    }

    fun getRootNode(branch: IBranch): INode {
        return PNodeAdapter(ITree.ROOT_ID, branch)
    }

//    fun connectToServer(url: String): IModelClient {
//        return RestWebModelClient(url)
//    }

    fun getBranch(node: INode): IBranch? {
        val unwrapped = deepUnwrapNode(node)
        return if (unwrapped is PNodeAdapter) {
            unwrapped.branch
        } else {
            null
        }
    }

    fun loadCurrentVersion(client: IModelClient, branch: BranchReference): CLVersion? {
        // require(client is RestWebModelClient)
        val versionHash = client.get(branch.getKey()) ?: return null
        return CLVersion.loadFromHash(versionHash, client.storeCache)
    }

    fun loadCurrentModel(client: IModelClient, branch: BranchReference): ITree? {
        return loadCurrentVersion(client, branch)?.getTree()
    }

    /**
     * The default thread-safe implementation of IBranch that enforces transactions.
     */
    fun newBranch(tree: ITree): IBranch = PBranch(tree, IdGenerator.getInstance(1))

    /**
     * A performance optimized branch that isn't thread-safe and doesn't enforce transactions.
     * Should only be used for short living instances that are guaranteed to be used by a single thread.
     * Not suitable for unit tests, because of the lack of enforcing transactions.
     * Unless performance isn't critical .newBranch should be used instead.
     */
    fun newUnsafeBranch(tree: ITree): IBranch = TreePointer(tree, IdGenerator.getInstance(1))

    @Deprecated("Use .toUnsafeBranch or .newBranch")
    fun toLocalBranch(tree: ITree): IBranch = TreePointer(tree, IdGenerator.getInstance(1))

    fun toNode(tree: ITree): INode {
        return PNodeAdapter(ITree.ROOT_ID, toLocalBranch(tree))
    }

    fun <T> readNode(node: INode, body: () -> T): T {
        return node.getArea().executeRead(body)
    }

    fun <T> writeNode(node: INode, body: () -> T): T {
        return node.getArea().executeWrite(body)
    }

    fun createBranchReference(repositoryId: RepositoryId, branchName: String? = null): BranchReference {
        return repositoryId.getBranchReference(branchName)
    }

    fun createRepositoryId(id: String): RepositoryId = RepositoryId(id)

    fun mergeUpdate(client: IModelClient, branch: BranchReference, baseVersionHash: String? = null, userName: String?, body: (IWriteTransaction) -> Unit): CLVersion {
        val store = client.storeCache.getAsyncStore()
        val actualBaseVersionHash: String = baseVersionHash
            ?: client.get(branch.getKey())
            ?: throw RuntimeException("$branch doesn't exist")
        val baseVersion = CLVersion.loadFromHash(actualBaseVersionHash, store)
        return applyUpdate(client, baseVersion, branch, userName, body)
    }

    private fun applyUpdate(
        client: IModelClient,
        baseVersion: CLVersion,
        branch: BranchReference,
        userId: String?,
        body: (IWriteTransaction) -> Unit,
    ): CLVersion {
        val otBranch = OTBranch(PBranch(baseVersion.getTree(), client.idGenerator), client.idGenerator)
        otBranch.computeWriteT { t -> body(t) }

        val operationsAndTree = otBranch.getPendingChanges()
        val newVersion = CLVersion.createRegularVersion(
            client.idGenerator.generate(),
            Clock.System.now().epochSeconds.toString(),
            userId,
            operationsAndTree.second as CLTree,
            baseVersion,
            operationsAndTree.first.map { it.getOriginalOp() }.toTypedArray(),
        )
        val currentVersion = loadCurrentVersion(client, branch)
            ?: throw RuntimeException("$branch doesn't exist")
        val mergedVersion = VersionMerger(client.storeCache, client.idGenerator)
            .mergeChange(currentVersion, newVersion)
        client.asyncStore.put(branch.getKey(), mergedVersion.getContentHash())
        // TODO handle concurrent write to the branchKey, otherwise versions might get lost. See ReplicatedRepository.
        return mergedVersion
    }
}
