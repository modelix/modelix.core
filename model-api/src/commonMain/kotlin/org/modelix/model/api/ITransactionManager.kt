package org.modelix.model.api

interface ITransactionManager {
    fun <R> executeRead(body: () -> R): R
    fun <R> executeWrite(body: () -> R): R
    fun canRead(): Boolean
    fun canWrite(): Boolean
}
