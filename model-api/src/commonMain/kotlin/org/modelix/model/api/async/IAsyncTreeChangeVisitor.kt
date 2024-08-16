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

package org.modelix.model.api.async

import com.badoo.reaktive.maybe.Maybe
import com.badoo.reaktive.single.Single

/**
 * Returning IAsyncValue<Unit> allows the event handler to execute further queries and guarantees that they are executed
 * before the event is considered as being fully processed.
 */
interface IAsyncTreeChangeVisitor {
    fun containmentChanged(nodeId: Long): Maybe<Unit>
    fun conceptChanged(nodeId: Long): Maybe<Unit>
    fun childrenChanged(nodeId: Long, role: String?): Maybe<Unit>
    fun referenceChanged(nodeId: Long, role: String): Maybe<Unit>
    fun propertyChanged(nodeId: Long, role: String): Maybe<Unit>

    fun interestedInNodeRemoveOrAdd(): Boolean
    fun nodeRemoved(nodeId: Long): Maybe<Unit>
    fun nodeAdded(nodeId: Long): Maybe<Unit>
}
