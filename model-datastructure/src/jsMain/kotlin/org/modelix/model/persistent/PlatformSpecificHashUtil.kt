package org.modelix.model.persistent

import Sha256
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

actual object PlatformSpecificHashUtil {
    actual fun sha256asByteArray(input: String): ByteArray {
        val hash = Sha256()
        hash.update(input)
        return hash.digestSync().asByteArray()
    }

    actual fun base64encode(input: ByteArray): String {
        return Base64.fromUint8Array(Uint8Array(input.toTypedArray()), true)
    }
}

@Suppress("UnsafeCastFromDynamic")
private fun Uint8Array.asByteArray(): ByteArray {
    return Int8Array(buffer, byteOffset, length).asDynamic()
}
