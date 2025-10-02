package org.modelix.datastructures.history

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.modelix.datastructures.objects.ObjectHash

@Serializable
data class HistoryEntry(
    val versionHash: ObjectHash,
    val time: Instant,
    val author: String?,
)
