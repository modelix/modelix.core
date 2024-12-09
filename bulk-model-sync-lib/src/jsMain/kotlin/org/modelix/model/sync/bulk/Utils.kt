package org.modelix.model.sync.bulk

actual fun isTty(): Boolean {
    // Be safe and never assume a terminal as we cannot easily test for a TTY being attached
    return false
}
