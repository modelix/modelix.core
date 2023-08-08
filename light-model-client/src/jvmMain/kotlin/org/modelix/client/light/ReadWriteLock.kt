package org.modelix.client.light

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal actual class ReadWriteLock {
    private val lock = ReentrantReadWriteLock()
    private val isReadLockedByCurrentThread: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
    actual fun <T> runRead(body: () -> T): T {
        return lock.read {
            val wasInRead = isReadLockedByCurrentThread.get()
            try {
                if (!wasInRead) isReadLockedByCurrentThread.set(true)
                body()
            } finally {
                if (!wasInRead) isReadLockedByCurrentThread.set(false)
            }
        }
    }
    actual fun <T> runWrite(body: () -> T): T = lock.write(body)
    actual fun canRead(): Boolean = lock.isWriteLockedByCurrentThread || isReadLockedByCurrentThread.get()

    actual fun canWrite(): Boolean = lock.isWriteLockedByCurrentThread
}
