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

package org.modelix.model.server.store

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * This is mostly equivalent to how MPS manages locks on the model.
 */
class TransactionLocks {
    private val readWriteLock = ReentrantReadWriteLock()
    private val readLock = readWriteLock.readLock()
    private val writeLock = readWriteLock.writeLock()

    fun assertRead() {
        check(canRead()) { "not inside a transaction" }
    }

    fun assertWrite() {
        check(canWrite()) { "not inside a write transaction" }
    }

    fun canRead() = readWriteLock.readHoldCount != 0 || readWriteLock.isWriteLockedByCurrentThread

    fun canWrite() = readWriteLock.isWriteLockedByCurrentThread

    fun <R> runRead(body: () -> R): R {
        check(!canWrite()) { "Starting " }
        return readLock.withLock(body)
    }

    fun <R> runWrite(body: () -> R): R {
        check(readWriteLock.readHoldCount == 0) {
            "deadlock prevention: do not start write transaction from read"
        }
        return writeLock.withLock(body)
    }
}
