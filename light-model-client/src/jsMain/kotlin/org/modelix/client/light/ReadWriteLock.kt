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
