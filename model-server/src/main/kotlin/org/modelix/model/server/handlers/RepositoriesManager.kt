/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.server.handlers

import kotlinx.datetime.Clock
import org.modelix.model.VersionMerger
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.BulkQuery
import org.modelix.model.lazy.CLHamtNode
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.CPNode
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.LocalModelClient
import org.modelix.model.server.store.pollEntry

/**
 * Multiple instances can be used at the same time, because there is no state outside the store.
 */
class RepositoriesManager(val client: LocalModelClient) {
    private val store: IStoreClient get() = client.store

    fun generateClientId(repositoryId: RepositoryId): Long {
        return client.store.generateId("$KEY_PREFIX:${repositoryId.id}:clientId")
    }

    fun getRepositoryNames(): Set<String> {
        return store[REPOSITORIES_LIST_KEY]?.lines()?.toSet() ?: emptySet()
    }

    fun createRepository(repositoryId: RepositoryId, userName: String?): CLVersion {
        var initialVersion: CLVersion? = null
        store.runTransaction {
            val masterBranch = repositoryId.getBranchReference()
            val existingRepositories = getRepositoryNames()
            if (existingRepositories.contains(repositoryId.id)) throw RepositoryAlreadyExistsException(repositoryId.id)
            store.put(REPOSITORIES_LIST_KEY, (existingRepositories + repositoryId.id).joinToString("\n"), false)
            store.put(branchListKey(repositoryId), masterBranch.branchName, false)
            initialVersion = CLVersion.createRegularVersion(
                id = client.idGenerator.generate(),
                time = Clock.System.now().epochSeconds.toString(),
                author = userName,
                tree = CLTree(client.storeCache),
                baseVersion = null,
                operations = emptyArray()
            )
            store.put(branchKey(masterBranch), initialVersion!!.hash, false)
        }
        return initialVersion!!
    }

    fun getBranches(repositoryId: RepositoryId): Set<String> {
        return store[branchListKey(repositoryId)]?.lines()?.toSet() ?: emptySet()
    }

    /**
     * Must be executed inside a transaction
     */
    private fun ensureBranchIsInList(branch: BranchReference) {
        val key = branchListKey(branch.repositoryId)
        val existingBranches = store[key]?.lines()?.toSet() ?: emptySet()
        if (!existingBranches.contains(branch.branchName)) {
            store.put(key, (existingBranches + branch.branchName).joinToString("\n"))
        }
    }

    fun mergeChanges(branch: BranchReference, newVersionHash: String): String {
        var result: String? = null
        store.runTransaction {
            val branchKey = branchKey(branch)
            val headHash = store[branchKey] ?: store[legacyBranchKey(branch)]
            val mergedHash = if (headHash == null) {
                newVersionHash
            } else {
                val headVersion = CLVersion(headHash, client.storeCache)
                val newVersion = CLVersion(newVersionHash, client.storeCache)
                require(headVersion.tree.getId() == newVersion.tree.getId()) {
                    "Attempt to merge a model with ID '${newVersion.tree.getId()}'" +
                            " into one with ID '${headVersion.tree.getId()}'"
                }
                val mergedVersion = VersionMerger(client.storeCache, client.idGenerator)
                    .mergeChange(headVersion, newVersion)
                mergedVersion.hash
            }
            store.put(branchKey, mergedHash, false)
            ensureBranchIsInList(branch)
            result = mergedHash
        }
        return result!!
    }

    fun getVersionHash(branch: BranchReference): String? {
        return store[branchKey(branch)]
    }

    suspend fun pollVersionHash(branch: BranchReference, lastKnown: String?): String {
        return pollEntry(client.store, branchKey(branch), lastKnown)
            ?: throw IllegalStateException("No version found for branch '${branch.branchName}' in repository '${branch.repositoryId}'")
    }

    fun computeDelta(versionHash: String, baseVersionHash: String?): Map<String, String?> {
        val changedNodeIds = HashSet<Long>()
        val oldAndNewEntries: Map<String, String?> = trackAccessedEntries { store ->
            val version = CLVersion(versionHash, store)

            version.operations

            val newTree = version.tree
            if (baseVersionHash == null) {
                newTree.root?.getDescendants(BulkQuery(store), true)?.execute()
            } else {
                val baseVersion = CLVersion(baseVersionHash, store)
                val oldTree = baseVersion.tree
                val bulkQuery = BulkQuery(store)
                newTree.nodesMap!!.visitChanges(oldTree.nodesMap!!, object : CLHamtNode.IChangeVisitor {
                    override fun visitChangesOnly(): Boolean = false
                    override fun entryAdded(key: Long, value: KVEntryReference<CPNode>?) {
                        changedNodeIds += key
                        if (value != null) bulkQuery.query(value, {})
                    }
                    override fun entryRemoved(key: Long, value: KVEntryReference<CPNode>?) {
                        changedNodeIds += key
                    }
                    override fun entryChanged(
                        key: Long,
                        oldValue: KVEntryReference<CPNode>?,
                        newValue: KVEntryReference<CPNode>?
                    ) {
                        changedNodeIds += key
                        if (newValue != null) bulkQuery.query(newValue, {})
                    }
                }, bulkQuery)
                bulkQuery.process()
            }
        }
        val oldEntries: Map<String, String?> = trackAccessedEntries { store ->
            if (baseVersionHash == null) return@trackAccessedEntries
            val baseVersion = CLVersion(baseVersionHash, store)
            baseVersion.operations
            val oldTree = baseVersion.tree
            val bulkQuery = BulkQuery(store)

            val nodesMap = oldTree.nodesMap!!
            changedNodeIds.forEach { changedNodeId ->
                nodesMap.get(changedNodeId, 0, bulkQuery).onSuccess { nodeRef ->
                    if (nodeRef != null) bulkQuery.query(nodeRef) {}
                }
            }
        }
        return oldAndNewEntries - oldEntries.keys
    }

    private fun trackAccessedEntries(body: (IDeserializingKeyValueStore) -> Unit): Map<String, String?> {
        val accessTrackingStore = AccessTrackingStore(client.asyncStore)
        val objectStore = ObjectStoreCache(accessTrackingStore)
        body(objectStore)
        return accessTrackingStore.accessedEntries
    }

    private fun branchKey(branch: BranchReference): String {
        return "$KEY_PREFIX:repositories:${branch.repositoryId.id}:branches:${branch.branchName}"
    }

    private fun legacyBranchKey(branchReference: BranchReference): String {
        return branchReference.getKey()
    }

    private fun branchListKey(repositoryId: RepositoryId) = "$KEY_PREFIX:repositories:${repositoryId.id}:branches"
    
    companion object {
        const val KEY_PREFIX = ":v2"
        private const val REPOSITORIES_LIST_KEY = "$KEY_PREFIX:repositories"
    }
}

class RepositoryAlreadyExistsException(val name: String) : IllegalStateException("Repository '$name' already exists")
