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
package org.modelix.model.server.api

import kotlinx.serialization.Serializable

@Serializable
data class ExceptionData(
    val message: String,
    val stacktrace: List<String>,
    val cause: ExceptionData? = null
) {
    constructor(exception: Throwable) : this(
        exception.message ?: "",
        exception.stackTraceToString().lines(),
        if (exception.cause == exception) null else exception.cause?.let { ExceptionData(it) }
    )

    fun allMessages() = generateSequence(this) { it.cause }.map { it.message }

    override fun toString(): String {
        return stacktrace.joinToString("\n")
    }
}