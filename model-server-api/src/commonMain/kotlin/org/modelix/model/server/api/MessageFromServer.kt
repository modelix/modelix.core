/*
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
package org.modelix.model.server.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MessageFromServer(
    val version: VersionData? = null,
    val replacedIds: Map<String, String>? = null,
    val includedChangeSets: List<ChangeSetId> = emptyList(),
    val exception: ExceptionData? = null
) {
    fun toJson() = Json.encodeToString(this)
    companion object {
        fun fromJson(json: String) = Json.decodeFromString<MessageFromServer>(json)
    }
}