package org.modelix.model.server

import org.modelix.model.server.store.IsolatingStore
import org.modelix.model.server.store.ObjectInRepository
import java.util.concurrent.atomic.AtomicLong

class StoreClientWithStatistics(val store: IsolatingStore) : IsolatingStore by store {
    private val totalRequests = AtomicLong()

    fun getTotalRequests() = totalRequests.get()

    override fun get(key: ObjectInRepository): String? {
        totalRequests.incrementAndGet()
        return store.get(key)
    }

    override fun getAll(keys: List<ObjectInRepository>): List<String?> {
        totalRequests.incrementAndGet()
        return store.getAll(keys)
    }

    override fun getAll(keys: Set<ObjectInRepository>): Map<ObjectInRepository, String?> {
        totalRequests.incrementAndGet()
        return store.getAll(keys)
    }

    override fun getAll(): Map<ObjectInRepository, String?> {
        totalRequests.incrementAndGet()
        return store.getAll()
    }
}
