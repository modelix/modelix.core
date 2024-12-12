package org.modelix.model.server

import org.modelix.model.server.store.IRepositoryAwareStore
import org.modelix.model.server.store.ObjectInRepository
import org.modelix.model.server.store.RequiresTransaction
import java.util.concurrent.atomic.AtomicLong

class StoreClientWithStatistics(val store: IRepositoryAwareStore) : IRepositoryAwareStore by store {
    private val totalRequests = AtomicLong()

    fun getTotalRequests() = totalRequests.get()

    @RequiresTransaction
    override fun get(key: ObjectInRepository): String? {
        totalRequests.incrementAndGet()
        return store.get(key)
    }

    @RequiresTransaction
    override fun getAll(keys: List<ObjectInRepository>): List<String?> {
        totalRequests.incrementAndGet()
        return store.getAll(keys)
    }

    @RequiresTransaction
    override fun getAll(keys: Set<ObjectInRepository>): Map<ObjectInRepository, String?> {
        totalRequests.incrementAndGet()
        return store.getAll(keys)
    }

    @RequiresTransaction
    override fun getAll(): Map<ObjectInRepository, String?> {
        totalRequests.incrementAndGet()
        return store.getAll()
    }
}
