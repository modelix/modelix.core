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

package org.modelix.model.server.store

import java.io.File
import java.io.IOException

private const val REPOSITORY_SEPARATOR = "@@"

fun IsolatingStore.loadDump(file: File): Int {
    var n = 0
    file.useLines { lines ->
        val entries = lines.associate { line ->
            val parts = line.split("#", limit = 2)
            n++
            parts[0] to parts[1]
        }.mapKeys {
            if (it.key.contains(REPOSITORY_SEPARATOR)) {
                ObjectInRepository(
                    it.key.substringAfterLast(REPOSITORY_SEPARATOR),
                    it.key.substringBeforeLast(
                        REPOSITORY_SEPARATOR,
                    ),
                )
            } else {
                ObjectInRepository.global(it.key)
            }
        }
        putAll(entries, silent = true)
    }
    return n
}

@Synchronized
@Throws(IOException::class)
fun IsolatingStore.writeDump(file: File) {
    file.writer().use { writer ->
        for ((key, value) in getAll()) {
            if (value == null) continue
            writer.append(key.key)
            if (!key.isGlobal()) {
                writer.append(REPOSITORY_SEPARATOR)
                writer.append(key.getRepositoryId())
            }
            writer.append("#")
            writer.append(value)
            writer.append("\n")
        }
    }
}
