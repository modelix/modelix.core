package org.modelix.model.client2

import kotlinx.coroutines.runBlocking
import org.modelix.model.IKeyListener
import org.modelix.model.IKeyValueStore
import org.modelix.model.IVersion
import org.modelix.model.async.BulkAsyncStore
import org.modelix.model.async.CachingAsyncStore
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.CacheConfiguration
import org.modelix.model.lazy.NonCachingObjectStore
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.HashUtil
import org.modelix.streams.FailingStreamExecutor
import org.modelix.streams.IStreamExecutor

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
            NonCachingObjectStore(ModelClientAsStore(this, repositoryId)).getAsyncStore(),
            cacheSize = config.cacheSize,
        ),
        batchSize = config.requestBatchSize,
    )
    return store.getStreamExecutor().query {
        CLVersion.tryLoadFromHash(versionHash, store).assertNotEmpty { "Version not found: $versionHash" }
    }
}

/**
 * An overload of [IModelClientV2.lazyLoadVersion] that reads the current version hash of the branch from the server and
 * then loads that version with lazy loading support.
 */
suspend fun IModelClientV2.lazyLoadVersion(branchRef: BranchReference, config: CacheConfiguration = CacheConfiguration()): IVersion {
    return lazyLoadVersion(branchRef.repositoryId, pullHash(branchRef), config)
}

private class ModelClientAsStore(client: IModelClientV2, val repositoryId: RepositoryId) :
    IKeyValueStore {
    private val client: IModelClientV2Internal = client as IModelClientV2Internal

    override fun getStreamExecutor(): IStreamExecutor = FailingStreamExecutor

    override fun get(key: String): String? {
        return getAll(listOf(key))[key]
    }

    override fun getIfCached(key: String): String? {
        return null
    }

    override fun put(key: String, value: String?) {
        putAll(mapOf(key to value))
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        return runBlocking {
            client.getObjects(repositoryId, keys.asSequence())
        }
    }

    override fun putAll(entries: Map<String, String?>) {
        runBlocking {
            client.pushObjects(
                repositoryId,
                entries.asSequence().map { (key, value) ->
                    require(HashUtil.isSha256(key) && value != null) { "Only immutable objects are allowed: $key -> $value" }
                    key to value
                },
            )
        }
    }

    override fun prefetch(key: String) {
        throw UnsupportedOperationException()
    }

    override fun listen(key: String, listener: IKeyListener) {
        throw UnsupportedOperationException()
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        throw UnsupportedOperationException()
    }

    override fun getPendingSize(): Int {
        return 0
    }
}
