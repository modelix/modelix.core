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
package org.modelix.model.server.api.v2

import io.ktor.http.ContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.Serializable

@Serializable
class VersionDelta(
    val versionHash: String,
    val baseVersionHash: String? = null,
    @Deprecated("use .objectsMap")
    val objects: Set<String> = emptySet(),
    val objectsMap: Map<String, String> = emptyMap(),
)

fun VersionDelta.asStream(): VersionDeltaStream {
    require(objects.isEmpty()) { "Legacy serialization not supported" }
    return VersionDeltaStream(
        versionHash = versionHash,
        objectsFlow = null,
        objectsSequence = objectsMap.asSequence().map { it.key to it.value },
    )
}

class VersionDeltaStream(
    val versionHash: String,
    val objectsFlow: Flow<Pair<String, String>>? = null,
    val objectsSequence: Sequence<Pair<String, String>>? = null,
) {
    companion object {
        val CONTENT_TYPE = ContentType("application", "x-modelix-objects")
    }

    fun getObjectsAsFlow(): Flow<Pair<String, String>> {
        return objectsFlow ?: objectsSequence?.asFlow() ?: emptyFlow()
    }
}

suspend fun <K, V> Flow<Pair<K, V>>.toMap(): Map<K, V> {
    val map = LinkedHashMap<K, V>()
    collect {
        map[it.first] = it.second
    }
    return map
}
