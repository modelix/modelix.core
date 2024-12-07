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
