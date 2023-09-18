/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.client.light

internal actual class ReadWriteLock {
    private var lock: LockType = LockType.Unlocked
    actual fun <T> runRead(body: () -> T): T {
        val previous = lock
        try {
            lock = LockType.Read
            return body()
        } finally {
            lock = previous
        }
    }

    actual fun <T> runWrite(body: () -> T): T {
        if (lock == LockType.Read) throw IllegalStateException("Cannot start write from read")
        val previous = lock
        try {
            lock = LockType.Write
            return body()
        } finally {
            lock = previous
        }
    }

    actual fun canRead(): Boolean = lock != LockType.Unlocked

    actual fun canWrite(): Boolean = lock == LockType.Write
}

private enum class LockType {
    Unlocked,
    Read,
    Write,
}
