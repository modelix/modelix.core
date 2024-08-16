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

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.maybe.maybeOf
import org.modelix.model.persistent.IKVValue

class NonBulkQuery(private val store: IDeserializingKeyValueStore) : IBulkQuery {

    override fun offerPrefetch(key: IPrefetchGoal) {
        // Since no real bulk queries are executed, prefetching doesn't provide any benefit.
    }

    override fun <T : IKVValue> query(hash: KVEntryReference<T>): Maybe<T> {
        return maybeOf(hash.getValue(store))
    }

    override fun executeQuery() {
        // all requests are processed immediately
    }
}
