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

package org.modelix.kotlin.utils

actual class AtomicLong actual constructor(initial: Long) {
    private var value: Long = initial
    actual fun incrementAndGet(): Long {
        return ++value
    }

    actual fun get(): Long {
        return value
    }

    actual fun set(newValue: Long) {
        value = newValue
    }

    actual fun addAndGet(delta: Long): Long {
        value += delta
        return value
    }
}

actual class AtomicBoolean actual constructor(initial: Boolean) {
    private var value: Boolean = initial
    actual fun get(): Boolean {
        return value
    }

    actual fun set(newValue: Boolean) {
        value = newValue
    }

    actual fun compareAndSet(expectedValue: Boolean, newValue: Boolean): Boolean {
        if (value == expectedValue) {
            value = newValue
            return true
        } else {
            return false
        }
    }

    actual fun getAndSet(newValue: Boolean): Boolean {
        val oldValue = value
        value = newValue
        return oldValue
    }

}