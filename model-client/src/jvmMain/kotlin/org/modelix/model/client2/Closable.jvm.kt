package org.modelix.model.client2

internal actual interface Closable : java.io.Closeable {
    actual override fun close()
}
