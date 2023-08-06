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
package org.modelix.modelql.core

import kotlin.jvm.JvmInline

@JvmInline
value class Optional<out E>(private val value: Any?) {
    fun isPresent(): Boolean = value != EMPTY
    fun get(): E {
        require(isPresent()) { "Optional value is not present" }
        return value as E
    }
    fun <R> map(body: (E) -> R): Optional<R> = if (isPresent()) of(body(get())) else empty()
    fun <R> flatMap(body: (E) -> Optional<R>): Optional<R> = if (isPresent()) body(get()) else empty()
    companion object {
        private object EMPTY
        fun <T> empty() = Optional<T>(EMPTY)
        fun <T> of(value: T) = Optional<T>(value)
    }
}

fun <T> Optional<T>.getOrElse(defaultValue: T): T = if (isPresent()) get() else defaultValue
fun <T> Optional<T>.presentAndEqual(other: T): Boolean = isPresent() && get() == other
