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

import org.modelix.model.persistent.IKVValue

interface IBulkQuery {
    fun executeQuery()
    fun <I, O> flatMap(input: Iterable<I>, f: (I) -> Value<O>): Value<List<O>>
    fun <T> constant(value: T): Value<T>
    fun <T : IKVValue> query(hash: KVEntryReference<T>): Value<T?>
    interface Value<out T> {
        fun executeQuery(): T
        fun <R> flatMap(handler: (T) -> Value<R>): Value<R>
        fun <R> map(handler: (T) -> R): Value<R>
        fun onReceive(handler: (T) -> Unit)
    }
}
