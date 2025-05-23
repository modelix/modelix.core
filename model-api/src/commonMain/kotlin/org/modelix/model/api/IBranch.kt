package org.modelix.model.api

/**
 * Representation of a branch.
 */
@Deprecated("Use IMutableModelTree")
interface IBranch {
    /**
     * Returns the id of this branch.
     *
     * @return branch id
     */
    fun getId(): String

    /**
     * Performs a read operation on this branch.
     *
     * @param runnable read operation to be performed
     */
    fun runRead(runnable: () -> Unit)

    /**
     * Performs a function in a read transaction on this branch.
     *
     * @param f function to be performed
     */
    fun runReadT(f: (IReadTransaction) -> Unit) {
        runRead { f(readTransaction) }
    }

    /**
     * Performs a computable read operation, which returns a value, on this branch.
     *
     * @param computable operation to be performed
     */
    fun <T> computeRead(computable: () -> T): T

    /**
     * Performs a computable read operation, which returns a value, in a read transaction on this branch.
     */
    fun <T> computeReadT(computable: (IReadTransaction) -> T): T {
        return computeRead { computable(readTransaction) }
    }

    /**
     * Performs a write operation on this branch.
     *
     * @param runnable operation to be performed
     */
    fun runWrite(runnable: () -> Unit)

    /**
     * Performs a function in a write transaction on this branch.
     *
     * @param f function to be performed
     */
    fun runWriteT(f: (IWriteTransaction) -> Unit) {
        runWrite { f(writeTransaction) }
    }

    /**
     * Performs a computable write operation, which returns a value, on this branch.
     *
     * @param computable operation to be performed
     */
    fun <T> computeWrite(computable: () -> T): T

    /**
     * Performs a computable write operation, which returns a value, in a write transaction on this branch.
     */
    fun <T> computeWriteT(computable: (IWriteTransaction) -> T): T {
        return computeWrite { computable(writeTransaction) }
    }

    /**
     * Checks if read operations can be performed on this branch.
     *
     * @return true if read operations can be performed, false otherwise
     */
    fun canRead(): Boolean

    /**
     * Checks if write operations can be performed on this branch.
     *
     * @return true if write operations can be performed, false otherwise
     */
    fun canWrite(): Boolean

    /**
     * Transaction for this branch.
     */
    val transaction: ITransaction

    /**
     * Read transaction for this branch.
     */
    val readTransaction: IReadTransaction

    /**
     * Write transaction for this branch.
     */
    val writeTransaction: IWriteTransaction

    /**
     * Adds the given branch listener to this branch.
     *
     * The branch listener will listen to changes on this branch.
     *
     * @param l branch listener to be added
     */
    fun addListener(l: IBranchListener)

    /**
     * Removes the given branch listener from this branch.
     *
     * @param l branch listener to be removed
     */
    fun removeListener(l: IBranchListener)
}

/**
 * Wrapper for [IBranch]
 */
interface IBranchWrapper : IBranch {
    /**
     * Unwraps the branch.
     *
     * @return unwrapped branch
     */
    fun unwrapBranch(): IBranch
}

/**
 * Recursively unwraps a branch.
 *
 * If the receiver is an [IBranchWrapper] it will be unwrapped else the branch will be returned.
 *
 * @return deep unwrapped branch
 */
fun IBranch.deepUnwrap(): IBranch = if (this is IBranchWrapper) unwrapBranch().deepUnwrap() else this
