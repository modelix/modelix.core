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
package org.modelix.kotlin.utils

expect class AtomicLong(initial: Long) {
    fun incrementAndGet(): Long
    fun get(): Long
    fun set(newValue: Long)
    fun addAndGet(delta: Long): Long
}

expect class AtomicBoolean(initial: Boolean) {
    fun get(): Boolean
    fun set(newValue: Boolean)
    fun compareAndSet(expectedValue: Boolean, newValue: Boolean): Boolean
    fun getAndSet(newValue: Boolean): Boolean
}
