/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.authorization.permissions

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class PermissionParts private constructor(val parts: List<String>, val currentIndex: Int) {
    constructor(vararg parts: String) : this(parts.toList(), 0)
    constructor(parts: List<String>) : this(parts.toList(), 0)
    val fullId: String get() = toString()
    fun next() = next(1)
    fun next(n: Int) = PermissionParts(parts, currentIndex + n)
    fun current() = parts[currentIndex]
    fun take(n: Int) = parts.drop(currentIndex).take(n)
    fun remainingSize() = parts.size - currentIndex
    operator fun plus(otherPart: String) = PermissionParts(parts + otherPart)
    operator fun plus(otherParts: List<String>) = PermissionParts(parts + otherParts)
    override fun toString(): String = toString(false)
    fun toString(noEscape: Boolean): String {
        return if (noEscape) {
            parts.joinToString("/")
        } else {
            parts.joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8) }
        }
    }

    companion object {
        fun fromString(fullId: String): PermissionParts {
            return PermissionParts(fullId.split('/').map { URLDecoder.decode(it, StandardCharsets.UTF_8) }, 0)
        }
    }
}
