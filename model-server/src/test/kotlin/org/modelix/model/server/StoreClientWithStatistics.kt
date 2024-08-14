/*
 * Copyright (c) 2024.
 *
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
        println("requested entries: ${keys.size}")
        totalRequests.incrementAndGet()
        val result = store.getAll(keys)
        result.forEach { println("    " + it.key.key + " = " + it.value) }
        return result
    }

    override fun getAll(): Map<ObjectInRepository, String?> {
        totalRequests.incrementAndGet()
        return store.getAll()
    }
}
