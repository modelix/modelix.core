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

package org.modelix.model.server.store

import org.modelix.model.IGenericKeyListener

/**
 * Stores immutable objects where the key is the SHA hash of the value, which means the entries never change.
 * Doesn't require transactions.
 */
interface IImmutableStore<KeyT> {
    fun getAll(keys: Set<KeyT>): Map<KeyT, String>
    fun addAll(entries: Map<KeyT, String>)
    fun getIfCached(key: KeyT): String?
}

fun <KeyT> IImmutableStore<KeyT>.asGenericStore() = ImmutableStoreAsGenericStore(this)

class ImmutableStoreAsGenericStore<KeyT>(val store: IImmutableStore<KeyT>) : IGenericStoreClient<KeyT> {
    override fun getAll(keys: Set<KeyT>): Map<KeyT, String?> {
        return store.getAll(keys)
    }

    override fun getAll(): Map<KeyT, String?> {
        throw UnsupportedOperationException()
    }

    override fun getIfCached(key: KeyT): String? {
        return store.getIfCached(key)
    }

    override fun putAll(entries: Map<KeyT, String?>, silent: Boolean) {
        store.addAll(entries.mapValues { requireNotNull(it.value) { "Deleting entries not allowed: $it" } })
    }

    override fun listen(key: KeyT, listener: IGenericKeyListener<KeyT>) {
        throw UnsupportedOperationException()
    }

    override fun removeListener(key: KeyT, listener: IGenericKeyListener<KeyT>) {
        throw UnsupportedOperationException()
    }

    override fun generateId(key: KeyT): Long {
        throw UnsupportedOperationException()
    }

    override fun getTransactionManager(): ITransactionManager {
        throw UnsupportedOperationException()
    }

    override fun getImmutableStore(): IImmutableStore<KeyT> {
        return store
    }

    override fun close() {
        throw UnsupportedOperationException()
    }
}
