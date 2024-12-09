package org.modelix.model.sync.bulk

actual fun isTty(): Boolean {
    return System.console() != null
}
