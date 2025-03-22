package org.modelix.model.client2

import org.modelix.model.IVersion
import org.modelix.model.async.BulkAsyncStore
import org.modelix.model.async.CachingAsyncStore
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.CacheConfiguration
import org.modelix.model.lazy.RepositoryId

/**
 * This function loads parts of the model lazily while it is iterated and limits the amount of data that is cached on
 * the client side.
 *
 * IModelClientV2#loadVersion eagerly loads the whole model. For large models this can be slow and requires lots of
 * memory.
 */
fun IModelClientV2.lazyLoadVersion(repositoryId: RepositoryId, versionHash: String, config: CacheConfiguration = CacheConfiguration()): IVersion {
    val store = BulkAsyncStore(
        CachingAsyncStore(
            ModelClientAsStore(this, repositoryId),
            cacheSize = config.cacheSize,
        ),
        batchSize = config.requestBatchSize,
    )
    return store.getStreamExecutor().query {
        CLVersion.Companion.tryLoadFromHash(versionHash, store).assertNotEmpty { "Version not found: $versionHash" }
    }
}

/**
 * An overload of [IModelClientV2.lazyLoadVersion] that reads the current version hash of the branch from the server and
 * then loads that version with lazy loading support.
 */
suspend fun IModelClientV2.lazyLoadVersion(branchRef: BranchReference, config: CacheConfiguration = CacheConfiguration()): IVersion {
    return lazyLoadVersion(branchRef.repositoryId, pullHash(branchRef), config)
}
