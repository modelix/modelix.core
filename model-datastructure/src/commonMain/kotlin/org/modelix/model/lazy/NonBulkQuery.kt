/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.lazy

import org.modelix.model.api.async.IAsyncValue
import org.modelix.model.persistent.IKVValue

class NonBulkQuery(private val store: IDeserializingKeyValueStore) : IBulkQuery {
    override fun <I, O> flatMap(input: Iterable<I>, f: (I) -> IAsyncValue<O>): IAsyncValue<List<O>> {
        val list = input.asSequence().map(f).map { it.awaitBlocking() }.toList()
        return Value(list)
    }

    override fun <T> constant(value: T): IAsyncValue<T> {
        return Value(value)
    }

    override fun offerPrefetch(key: IPrefetchGoal) {
        // Since no real bulk queries are executed, prefetching doesn't provide any benefit.
    }

    override fun <T : IKVValue> query(hash: KVEntryReference<T>): IAsyncValue<T?> {
        return constant(hash.getValue(store))
    }

    override fun executeQuery() {
        // all requests are processed immediately
    }

    class Value<T>(private val value: T) : IAsyncValue<T> {
        override fun <R> flatMap(handler: (T) -> IAsyncValue<R>): IAsyncValue<R> {
            return handler(value)
        }

        override fun <R> map(handler: (T) -> R): IAsyncValue<R> {
            return Value(handler(value))
        }

        override fun onReceive(handler: (T) -> Unit) {
            handler(value)
        }

        override suspend fun await(): T {
            return value
        }

        override fun awaitBlocking(): T {
            return value
        }
    }
}
