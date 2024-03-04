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

package org.modelix.mps.sync.util

import org.modelix.kotlin.utils.UnstableModelixFeature
import java.util.concurrent.CompletableFuture

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun CompletableFuture<Any?>.bindTo(other: CompletableFuture<Any?>): CompletableFuture<Any?> {
    this.handle { result, throwable ->
        if (throwable != null) {
            other.completeExceptionally(throwable)
        } else {
            other.complete(result)
        }
    }
    return this
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun CompletableFuture<Any?>.completeWithDefault(): CompletableFuture<Any?> {
    this.complete(CompletableFutureDefaultResult)
    return this
}

/**
 * This class is used instead of null, because if CompletableFuture.result == null then it means the Future is not
 * done yet...
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object CompletableFutureDefaultResult
