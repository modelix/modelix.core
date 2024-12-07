package org.modelix.model.persistent

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Base64

actual object PlatformSpecificHashUtil {
    actual fun sha256asByteArray(input: String): ByteArray {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(input.encodeToByteArray(throwOnInvalidSequence = true))
            return digest.digest()
        } catch (ex: NoSuchAlgorithmException) {
            throw RuntimeException(ex)
        }
    }

    actual fun base64encode(input: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input)
    }
}
