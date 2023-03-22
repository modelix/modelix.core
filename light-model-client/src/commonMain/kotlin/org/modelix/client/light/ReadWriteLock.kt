package org.modelix.client.light

internal expect class ReadWriteLock() {
    fun <T> runRead(body: () -> T): T
    fun <T> runWrite(body: () -> T): T
    fun canRead(): Boolean
    fun canWrite(): Boolean
}