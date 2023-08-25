package org.modelix.model.persistent

import Sha256
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

@JsModule("js-base64")
@JsNonModule
external object Base64 {
    fun fromUint8Array(input: Uint8Array, uriSafe: Boolean): String
    fun decode(input: String): String
    fun encode(input: String): String
    fun encode(input: String, uriSafe: Boolean): String
}

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
