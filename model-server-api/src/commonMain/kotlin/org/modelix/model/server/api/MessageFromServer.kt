package org.modelix.model.server.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MessageFromServer(
    val version: VersionData? = null,
    val replacedIds: Map<String, String>? = null,
    val appliedChangeSet: ChangeSetId? = null,
    val exception: ExceptionData? = null,
) {
    fun toJson() = Json.encodeToString(this)
    companion object {
        fun fromJson(json: String) = Json.decodeFromString<MessageFromServer>(json)
    }
}
