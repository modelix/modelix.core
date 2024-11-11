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

import com.badoo.reaktive.maybe.Maybe
import org.modelix.model.persistent.IKVValue
import kotlin.jvm.Synchronized

@Deprecated("use IAsyncStore")
class SynchronizedBulkQuery(val nonThreadSafeQuery: IBulkQuery) : IBulkQuery {
    @Synchronized
    override fun offerPrefetch(key: IPrefetchGoal) {
        return nonThreadSafeQuery.offerPrefetch(key)
    }

    @Synchronized
    override fun executeQuery() {
        return nonThreadSafeQuery.executeQuery()
    }

    @Synchronized
    override fun <T : IKVValue> query(hash: IKVEntryReference<T>): Maybe<T> {
        return nonThreadSafeQuery.query(hash)
    }
}

fun IBulkQuery.asSynchronized(): IBulkQuery {
    return if (this is SynchronizedBulkQuery) this else SynchronizedBulkQuery(this)
}
