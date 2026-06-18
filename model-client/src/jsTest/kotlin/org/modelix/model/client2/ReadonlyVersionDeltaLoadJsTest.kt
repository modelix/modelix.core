package org.modelix.model.client2

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.api.v2.VersionDeltaStreamV2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * B2a (#1042): readonly versions should be loaded as a delta against the previously loaded
 * version of the same repository, so the surviving object graph is reused instead of being
 * reloaded in full on every version switch. The presence of the `lastKnown` query parameter is
 * what tells the server to send a delta, so it is the observable signal we assert on.
 */
class ReadonlyVersionDeltaLoadJsTest {

    /** A real, offline version: its root hash plus the serialized objects of its full closure. */
    private class VersionFixture(val versionHash: String, val objects: List<Pair<String, String>>)

    /** A client wired to a [MockEngine] that records the `lastKnown` parameter of every request. */
    private class RecordingClient(val client: ClientJSImpl, val lastKnownParams: List<String?>)

    /** Builds a single, real, offline [CLVersion] and serializes its whole object closure. */
    private fun buildVersionFixture(): VersionFixture {
        val fixtureClient = ModelClientV2.builder().url("http://localhost/v2").build()
        val graph = ModelClientGraph(fixtureClient, RepositoryId("fixture"))
        val tree = CLTree.builder(graph).build()
        val version = CLVersion.createRegularVersion(
            id = 1L,
            time = null,
            author = "test",
            tree = tree,
            baseVersion = null,
            operations = emptyArray(),
            graph = graph,
        )
        val objects = graph.getStreamExecutor().query {
            version.obj.objectDiff(null).map { it.getHashString() to it.data.serialize() }.toList()
        }
        return VersionFixture(version.getObjectHash().toString(), objects)
    }

    /** Encodes a [VersionDeltaStreamV2] body containing exactly the given objects. */
    private fun encodeDelta(versionHash: String, objects: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        fun line(value: String) = sb.append(value).append('\n').append('$').append('\n')
        line(versionHash)
        for ((hash, obj) in objects) {
            line(hash)
            line(obj)
        }
        sb.append('~')
        return sb.toString()
    }

    /** A [ClientJSImpl] whose engine always returns [fixture] and records each `lastKnown` param. */
    private fun recordingClient(fixture: VersionFixture): RecordingClient {
        val deltaBody = encodeDelta(fixture.versionHash, fixture.objects)
        val lastKnownParams = mutableListOf<String?>()
        val mockEngine = MockEngine { request ->
            lastKnownParams.add(request.url.parameters["lastKnown"])
            respond(
                deltaBody,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, VersionDeltaStreamV2.CONTENT_TYPE.toString()),
            )
        }
        val modelClient = ModelClientV2.builder()
            .client(HttpClient(mockEngine))
            .url("http://localhost/v2")
            .build()
        return RecordingClient(ClientJSImpl(modelClient), lastKnownParams)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun reusesLastLoadedReadonlyVersionAsBaseVersion() = GlobalScope.promise {
        val fixture = buildVersionFixture()
        val recording = recordingClient(fixture)

        // First readonly load of repo1: nothing known yet -> full load.
        recording.client.loadReadonlyVersion("repo1", fixture.versionHash).await()
        // Second readonly load of repo1: the previously loaded version must be the delta base.
        recording.client.loadReadonlyVersion("repo1", fixture.versionHash).await()
        // A different repo has its own (empty) memo -> full load again.
        recording.client.loadReadonlyVersion("repo2", fixture.versionHash).await()

        val params = recording.lastKnownParams
        assertEquals(3, params.size, "expected one request per readonly load")
        assertNull(params[0], "first load of a repo must not send a delta base")
        assertEquals(fixture.versionHash, params[1], "second load of the same repo must send the previous version as delta base")
        assertNull(params[2], "first load of a different repo must not send a delta base")
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun readonlyMemoIsSharedAcrossLoadSites() = GlobalScope.promise {
        val fixture = buildVersionFixture()
        val recording = recordingClient(fixture)

        // Load a readonly version via startReplicatedModels...
        recording.client.startReplicatedModels(
            arrayOf(
                ReplicatedModelParameters(
                    repositoryId = "repo1",
                    idScheme = IdSchemeJS.READONLY,
                    readonly = true,
                    versionHash = fixture.versionHash,
                ),
            ),
        ).await()
        // ...then load another readonly version of the same repo via loadReadonlyVersion.
        recording.client.loadReadonlyVersion("repo1", fixture.versionHash).await()

        val params = recording.lastKnownParams
        assertEquals(2, params.size, "expected one request per readonly load")
        assertNull(params[0], "first readonly load of a repo must not send a delta base")
        assertEquals(
            fixture.versionHash,
            params[1],
            "loadReadonlyVersion must reuse the version remembered by startReplicatedModels",
        )
    }
}
