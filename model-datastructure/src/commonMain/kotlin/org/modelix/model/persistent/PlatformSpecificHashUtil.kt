package org.modelix.model.persistent

expect object PlatformSpecificHashUtil {
    fun sha256asByteArray(input: String): ByteArray
    fun base64encode(input: ByteArray): String
}
