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

package org.modelix.streams

import com.badoo.reaktive.observable.Observable
import com.badoo.reaktive.observable.observableUnsafe
import com.badoo.reaktive.single.Single
import com.badoo.reaktive.single.asObservable
import org.modelix.kotlin.utils.ContextValue

class SynchronousPipeline {
    val afterRootSubscribed = ArrayList<() -> Unit>()

    companion object {
        val contextValue = ContextValue<SynchronousPipeline>()
    }
}

fun <T> Single<T>.endOfSynchronousPipeline(): Single<T> = asObservable().endOfSynchronousPipeline().exactlyOne()

fun <T> Observable<T>.endOfSynchronousPipeline(): Observable<T> {
    val upstream = this
    return observableUnsafe<T> { observer ->
        val pipeline = SynchronousPipeline()
        SynchronousPipeline.contextValue.computeWith(pipeline) {
            upstream.subscribe(observer)
            pipeline.afterRootSubscribed.toList().forEach { it() }
        }
    }
}
