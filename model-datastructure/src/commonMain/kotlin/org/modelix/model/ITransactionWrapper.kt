package org.modelix.model

import org.modelix.model.api.ITransaction

interface ITransactionWrapper : ITransaction {
    fun unwrap(): ITransaction
}

fun ITransaction.unwrapAll(): List<ITransaction> {
    return if (this is ITransactionWrapper) listOf(this) + this.unwrap().unwrapAll() else listOf(this)
}
