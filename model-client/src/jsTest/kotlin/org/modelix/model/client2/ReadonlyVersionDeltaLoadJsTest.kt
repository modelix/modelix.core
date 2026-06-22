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
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.api.v2.VersionDeltaStreamV2
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B2a (#1042): readonly versions are loaded as a delta against the previously loaded version of the same
 * repository, so only the changed tree objects are transferred on a version switch. The request carries an
 * [ObjectDeltaFilter] (the observable signal): its `knownVersions` is the delta base, and history/operations
 * are excluded because readonly browsing only needs the tree.
 */
class ReadonlyVersionDeltaLoadJsTest {

    /** A real, offline version: its root hash plus the serialized objects of its full closure. */
    private class VersionFixture(val versionHash: String, val objects: List<Pair<String, String>>)

    /** A client wired to a [MockEngine] that records the parsed `filter` of every request. */
    private class RecordingClient(val client: ClientJSImpl, val filters: List<ObjectDeltaFilter?>)

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

    /** A [ClientJSImpl] whose engine always returns [fixture] and records each request's parsed `filter`. */
    private fun recordingClient(fixture: VersionFixture): RecordingClient {
        val deltaBody = encodeDelta(fixture.versionHash, fixture.objects)
        val filters = mutableListOf<ObjectDeltaFilter?>()
        val mockEngine = MockEngine { request ->
            filters.add(request.url.parameters["filter"]?.let { ObjectDeltaFilter.fromJson(it) })
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
        return RecordingClient(ClientJSImpl(modelClient), filters)
    }

    private fun assertLeanReadonlyFilter(filter: ObjectDeltaFilter?, expectedBase: String?, message: String) {
        checkNotNull(filter) { "$message: expected a readonly delta filter on the request" }
        assertEquals(setOfNotNull(expectedBase), filter.knownVersions, "$message: wrong delta base")
        assertEquals(false, filter.includeHistory, "$message: readonly load must not request history")
        assertEquals(false, filter.includeOperations, "$message: readonly load must not request operations")
        assertEquals(true, filter.includeTrees, "$message: readonly load must request the tree")
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun reusesLastLoadedReadonlyVersionAsBaseVersion() = GlobalScope.promise {
        val fixture = buildVersionFixture()
        val recording = recordingClient(fixture)

        // First readonly load of repo1: nothing known yet -> no delta base.
        recording.client.loadReadonlyVersion("repo1", fixture.versionHash).await()
        // Second readonly load of repo1: the previously loaded version must be the delta base.
        recording.client.loadReadonlyVersion("repo1", fixture.versionHash).await()
        // A different repo has its own (empty) memo -> no delta base.
        recording.client.loadReadonlyVersion("repo2", fixture.versionHash).await()

        val filters = recording.filters
        assertEquals(3, filters.size, "expected one request per readonly load")
        assertLeanReadonlyFilter(filters[0], expectedBase = null, message = "first load of a repo")
        assertLeanReadonlyFilter(filters[1], expectedBase = fixture.versionHash, message = "second load of the same repo")
        assertLeanReadonlyFilter(filters[2], expectedBase = null, message = "first load of a different repo")
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

        val filters = recording.filters
        assertEquals(2, filters.size, "expected one request per readonly load")
        assertLeanReadonlyFilter(filters[0], expectedBase = null, message = "first readonly load of a repo")
        assertLeanReadonlyFilter(
            filters[1],
            expectedBase = fixture.versionHash,
            message = "loadReadonlyVersion must reuse the version remembered by startReplicatedModels",
        )
    }
}
