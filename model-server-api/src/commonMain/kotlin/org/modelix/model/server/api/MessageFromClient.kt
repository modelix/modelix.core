package org.modelix.model.server.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

typealias ChangeSetId = Int

@Serializable
data class MessageFromClient(
    val changeSetId: ChangeSetId? = null,
    val operations: List<OperationData>? = null,
    val query: ModelQuery? = null,
    val baseVersionHash: String? = null,
    val baseChangeSet: ChangeSetId? = null,
) {
    fun toJson() = Json.encodeToString(this)
    companion object {
        fun fromJson(json: String) = Json.decodeFromString<MessageFromClient>(json)
    }
}
