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

import java.lang.ThreadLocal as JvmThreadLocal

actual class ThreadLocal<E> actual constructor(initialValueSupplier: () -> E) {

    private val threadLocal = JvmThreadLocal.withInitial(initialValueSupplier)

    actual fun get(): E {
        return threadLocal.get()
    }

    actual fun set(value: E) {
        return threadLocal.set(value)
    }

    actual fun remove() {
        return threadLocal.remove()
    }
}
