package org.modelix.model.server.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

interface ITransactionManager {
    fun canRead(): Boolean
    fun canWrite(): Boolean
    fun <R> runRead(body: () -> R): R
    fun <R> runWrite(body: () -> R): R
}

suspend fun <R> ITransactionManager.runWriteIO(body: () -> R): R {
    return withContext(Dispatchers.IO) {
        runWrite(body)
    }
}

suspend fun <R> ITransactionManager.runReadIO(body: () -> R): R {
    return withContext(Dispatchers.IO) {
        runRead(body)
    }
}

fun ITransactionManager.assertRead() {
    if (!canRead()) throw MissingReadTransactionException()
}

fun ITransactionManager.assertWrite() {
    if (!canWrite()) throw MissingWriteTransactionException()
}

abstract class MissingTransactionException(message: String) : RuntimeException(message)
class MissingReadTransactionException() : MissingTransactionException("Read transaction required")
class MissingWriteTransactionException() : MissingTransactionException("Write transaction required")

/**
 * This is mostly equivalent to how MPS manages locks on the model.
 */
class TransactionLocks : ITransactionManager {
    private val readWriteLock = ReentrantReadWriteLock()
    private val readLock = readWriteLock.readLock()
    private val writeLock = readWriteLock.writeLock()

    override fun canRead() = readWriteLock.readHoldCount != 0 || readWriteLock.isWriteLockedByCurrentThread

    override fun canWrite() = readWriteLock.isWriteLockedByCurrentThread && readWriteLock.readHoldCount == 0

    override fun <R> runRead(body: () -> R): R {
        return readLock.withLock(body)
    }

    override fun <R> runWrite(body: () -> R): R {
        check(readWriteLock.readHoldCount == 0) {
            "deadlock prevention: do not start write transaction from read"
        }
        return writeLock.withLock(body)
    }
}
