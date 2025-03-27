package org.modelix.model.server

import org.modelix.model.server.store.IRepositoryAwareStore
import org.modelix.model.server.store.ObjectInRepository
import org.modelix.model.server.store.RequiresTransaction
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

class StoreClientWithStatistics(val store: IRepositoryAwareStore) : IRepositoryAwareStore by store {
    private val totalRequests = AtomicLong()
    private val totalObjects = AtomicLong()
    private val maxRequestSize = AtomicLong()

    fun resetMaxRequestSize() { maxRequestSize.set(0) }
    fun getMaxRequestSize() = maxRequestSize.get()
    fun getTotalRequests() = totalRequests.get()
    fun getTotalObjects() = totalObjects.get()

    @RequiresTransaction
    override fun get(key: ObjectInRepository): String? {
        totalRequests.incrementAndGet()
        totalObjects.incrementAndGet()
        maxRequestSize.getAndUpdate { max(it, 1) }
        return store.get(key)
    }

    @RequiresTransaction
    override fun getAll(keys: List<ObjectInRepository>): List<String?> {
        totalRequests.incrementAndGet()
        totalObjects.addAndGet(keys.size.toLong())
        maxRequestSize.getAndUpdate { max(it, keys.size.toLong()) }
        return store.getAll(keys)
    }

    @RequiresTransaction
    override fun getAll(keys: Set<ObjectInRepository>): Map<ObjectInRepository, String?> {
        totalRequests.incrementAndGet()
        totalObjects.addAndGet(keys.size.toLong())
        maxRequestSize.getAndUpdate { max(it, keys.size.toLong()) }
        return store.getAll(keys)
    }

    @RequiresTransaction
    override fun getAll(): Map<ObjectInRepository, String?> {
        totalRequests.incrementAndGet()
        return store.getAll()
    }
}
