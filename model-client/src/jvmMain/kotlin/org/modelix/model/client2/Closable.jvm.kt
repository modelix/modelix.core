package org.modelix.model.client2

actual interface Closable : java.io.Closeable {
    actual override fun close()
}
