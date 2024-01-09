/*
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
package org.modelix.model.persistent

import org.modelix.model.lazy.IBulkQuery
import org.modelix.model.lazy.KVEntryReference

/**
 * Serializable object that can be stored in a key value store
 */
interface IKVValue {
    var ref: KVEntryReference<IKVValue>?
    fun serialize(): String
    val hash: String
    fun getDeserializer(): KVEntryReference.IDeserializer<*>
    fun getReferencedEntries(): List<KVEntryReference<IKVValue>>
    fun load(bulkQuery: IBulkQuery, reusableCandidate: KVEntryReference<*>?)
}

fun <T : IKVValue> T.getUpcastedDeserializer() = getDeserializer() as KVEntryReference.IDeserializer<T>
