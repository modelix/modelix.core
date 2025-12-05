package org.modelix.model.server.store

import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.lazy.diff
import org.modelix.streams.iterateBlocking

class GlobalStorageMigration(val stores: StoreManager) {
    @RequiresTransaction
    fun copyReachableObjectsToIsolatedStorage(repositoryId: RepositoryId, versionHashes: Set<String>) {
        if (versionHashes.isEmpty()) return

        val sourceStore = stores.getAsyncStore(null) // global storage
        val objectsToCopy = mutableMapOf<ObjectInRepository, String>()

        // Collect all reachable object hashes from the repository's versions
        val reachableHashes = mutableSetOf<String>()

        for (versionHash in versionHashes) {
            // Add the version hash itself
            reachableHashes.add(versionHash)

            // Load the version and collect all objects it references
            val version = CLVersion.loadFromHash(versionHash, sourceStore)

            // Use diff with empty list to get all objects reachable from this version
            version.diff(emptyList()).iterateBlocking(sourceStore) { obj ->
                reachableHashes.add(obj.getHashString())
            }
        }

        // Copy all reachable objects to isolated storage
        for (hash in reachableHashes) {
            val globalKey = ObjectInRepository.global(hash)
            val value = stores.genericStore[globalKey]
            if (value != null) {
                val isolatedKey = ObjectInRepository(repositoryId.id, hash)
                objectsToCopy[isolatedKey] = value
            }
        }

        stores.genericStore.putAll(objectsToCopy, silent = true)
    }
}
