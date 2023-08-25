/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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
