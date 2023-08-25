@file:JsModule("@aws-crypto/sha256-js")

import org.khronos.webgl.Uint8Array

external class Sha256 {
    fun update(input: String)
    fun digestSync(): Uint8Array
}
