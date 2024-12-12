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
        @OptIn(RequiresTransaction::class)
        getTransactionManager().runWrite {
            putAll(entries, silent = true)
        }
    }
    return n
}

@Synchronized
@Throws(IOException::class)
fun IsolatingStore.writeDump(file: File) {
    @OptIn(RequiresTransaction::class)
    getTransactionManager().runRead {
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
}
