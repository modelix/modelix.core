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

package org.modelix.model.lazy

import org.modelix.model.api.runSynchronized
import org.modelix.model.persistent.IKVValue
import kotlin.jvm.Synchronized

class SynchronizedBulkQuery(val nonThreadSafeQuery: IBulkQuery) : IBulkQuery {
    @Synchronized
    override fun <T> constant(value: T): IBulkQuery.Value<T> {
        return nonThreadSafeQuery.constant(value)
    }

    @Synchronized
    override fun offerPrefetch(key: IPrefetchGoal) {
        return nonThreadSafeQuery.offerPrefetch(key)
    }

    @Synchronized
    override fun executeQuery() {
        return nonThreadSafeQuery.executeQuery()
    }

    @Synchronized
    override fun <I, O> flatMap(input: Iterable<I>, f: (I) -> IBulkQuery.Value<O>): IBulkQuery.Value<List<O>> {
        return nonThreadSafeQuery.flatMap(input, f)
    }

    @Synchronized
    override fun <T : IKVValue> query(hash: KVEntryReference<T>): IBulkQuery.Value<T?> {
        return nonThreadSafeQuery.query(hash)
    }

    inner class Value<E>(val nonThreadSafeValue: IBulkQuery.Value<E>) : IBulkQuery.Value<E> {
        override fun executeQuery(): E {
            runSynchronized(this@SynchronizedBulkQuery) {
                return nonThreadSafeValue.executeQuery()
            }
        }

        override fun <R> flatMap(handler: (E) -> IBulkQuery.Value<R>): IBulkQuery.Value<R> {
            runSynchronized(this@SynchronizedBulkQuery) {
                return nonThreadSafeValue.flatMap(handler)
            }
        }

        override fun <R> map(handler: (E) -> R): IBulkQuery.Value<R> {
            runSynchronized(this@SynchronizedBulkQuery) {
                return nonThreadSafeValue.map(handler)
            }
        }

        override fun onReceive(handler: (E) -> Unit) {
            runSynchronized(this@SynchronizedBulkQuery) {
                return nonThreadSafeValue.onReceive(handler)
            }
        }
    }
}

fun IBulkQuery.asSynchronized(): IBulkQuery {
    return if (this is SynchronizedBulkQuery) this else SynchronizedBulkQuery(this)
}
