package org.modelix.model.server.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * Each call of runRead/runWrite can be annotated with @OptIn(RequiresTransaction::class)
 * All other usages should propagate this annotation.
 *
 * Unfortunately, there is no way to automatically opt in the body of runRead/runWrite.
 * Checked exceptions in Java would allow stopping the propagation based on the execution flow,
 * but Kotlin doesn't have checked exceptions.
 * It's still better having to annotate everything instead of noticing missing transactions only at runtime.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
annotation class RequiresTransaction

interface ITransactionManager {
    fun canRead(): Boolean
    fun canWrite(): Boolean
    fun <R> runRead(body: () -> R): R
    fun <R> runWrite(body: () -> R): R
}

class NoTransactionManager : ITransactionManager {
    override fun canRead(): Boolean = true

    override fun canWrite(): Boolean = true

    override fun <R> runRead(body: () -> R): R {
        return body()
    }

    override fun <R> runWrite(body: () -> R): R {
        return body()
    }
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

@RequiresTransaction
fun ITransactionManager.assertRead() {
    if (!canRead()) throw MissingTransactionException()
}

@RequiresTransaction
fun ITransactionManager.assertWrite() {
    if (!canWrite()) throw MissingWriteTransactionException()
}

open class MissingTransactionException(message: String = "Transaction required") : Exception(message)
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
