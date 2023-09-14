/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.modelix.authorization.installAuthentication
import org.modelix.model.LinearHistory
import org.modelix.model.ModelFacade
import org.modelix.model.VersionMerger
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IdGeneratorDummy
import org.modelix.model.api.PBranch
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.data.asData
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.OTBranch
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.store.InMemoryStoreClient
import org.modelix.model.test.RandomModelChangeGenerator
import java.util.Collections
import java.util.SortedSet
import java.util.TreeSet
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class ReplicatedRepositoryTest {

    private fun runTest(block: suspend ApplicationTestBuilder.(scope: CoroutineScope) -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets)
            ModelReplicationServer(InMemoryStoreClient()).init(this)
        }

        coroutineScope {
            block(this)
        }
    }

    @Test
    fun `sequential write from multiple clients`() = runTest {
        val url = "http://localhost/v2"
        val modelClient = ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        val modelClient2 = ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        val repositoryId = RepositoryId("repo1")
        modelClient.initRepository(repositoryId)

        val replicatedModel = modelClient.getReplicatedModel(repositoryId.getBranchReference())
        val replicatedModel2 = modelClient2.getReplicatedModel(repositoryId.getBranchReference())
        val branch1 = replicatedModel.start()
        val branch2 = replicatedModel2.start()

        val rand = Random(34554)

        for (changeId in 1..10) {
            println("change set $changeId")
            val branchToChange = if (rand.nextBoolean()) {
                println("changing branch 1")
                branch1
            } else {
                println("changing branch 2")
                branch2
            }
            branchToChange.runWrite {
                val changeGenerator = RandomModelChangeGenerator(branchToChange.getRootNode(), rand)
                for (i in 1..1000) changeGenerator.applyRandomChange()
                println("new tree: " + (branchToChange.transaction.tree as CLTree).hash)
            }

            val syncTime = measureTime {
                for (timeout in 1..1000) {
                    if (branch1.treeHash() == branch2.treeHash()) break
                    delay(1.milliseconds)
                }
            }
            println("synced after $syncTime")
            val data1 = branch1.computeRead {
                println("reading on branch 1: " + branch1.treeHash())
                branch1.getRootNode().asData()
            }
            val data2 = branch2.computeRead {
                println("reading on branch 2: " + branch2.treeHash())
                branch2.getRootNode().asData()
            }
            assertEquals(data1, data2)
        }
    }

    @Test fun `concurrent write 0`() = concurrentWrite(0)

    @Test fun `concurrent write 1`() = concurrentWrite(1)

    @Test fun `concurrent write 2`() = concurrentWrite(2)

    @Test fun `concurrent write 3`() = concurrentWrite(3)

    @Test fun `concurrent write 4`() = concurrentWrite(4)

    @Test fun `concurrent write 5`() = concurrentWrite(5)

    @Test fun `concurrent write 6`() = concurrentWrite(6)

    @Test fun `concurrent write 7`() = concurrentWrite(7)

    @Test fun `concurrent write 8`() = concurrentWrite(8)

    @Test fun `concurrent write 9`() = concurrentWrite(9)

    fun concurrentWrite(iteration: Int) = runTest { scope ->
        val url = "http://localhost/v2"
        val clients = (1..3).map {
            ModelClientV2.builder().url(url).client(client).build().also { it.init() }
        }

        val repositoryId = RepositoryId("repo1")
        val initialVersion = clients[0].initRepository(repositoryId)
        val branchId = repositoryId.getBranchReference("my-branch")
        clients[0].push(branchId, initialVersion, initialVersion)
        val models = clients.map { client -> client.getReplicatedModel(branchId, scope).also { it.start() } }

        try {
            val createdNodes: MutableSet<String> = Collections.synchronizedSet(TreeSet<String>())

            coroutineScope {
                suspend fun launchWriter(model: ReplicatedModel, seed: Int) {
                    launch {
                        val rand = Random(seed)
                        for (i in 1..10) {
                            delay(rand.nextLong(50, 100))
                            model.getBranch().runWriteT { t ->
                                createdNodes += t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?).toString(16)
                            }
                        }
                    }
                }
                models.forEachIndexed { index, model ->
                    launchWriter(model, 56456 + index + iteration * 100000)
                    delay(200.milliseconds)
                }
            }

            fun getChildren(model: ReplicatedModel): SortedSet<String> {
                return getChildren(model.getBranch())
            }

            assertEquals(clients.size * 10, createdNodes.size)

            println("writing done. waiting for convergence.")
            runCatching {
                withTimeout(30.seconds) {
                    models.forEach { model ->
                        while (getChildren(model) != createdNodes) delay(100.milliseconds)
                    }
                }
            }

            // models.forEach { it.resetToServerVersion() }

            val serverVersion = clients[0].pull(branchId, null)
            val childrenOnServer = getChildren(PBranch(serverVersion.getTree(), IdGeneratorDummy()))

            assertEquals(createdNodes, childrenOnServer)

            for (model in models) {
                assertEquals(createdNodes, getChildren(model))
            }
        } finally {
            models.forEach { it.dispose() }
        }
        println("all successful")
    }

    @Ignore
    @Test
    fun mergePerformanceTest() {
        val rand = Random(917563)
        val idGenerator = IdGenerator.getInstance(100)
        val initialTree = ModelFacade.newLocalTree() as CLTree
        val initialVersion = CLVersion.createRegularVersion(
            idGenerator.generate(),
            null,
            null,
            initialTree,
            null,
            emptyArray(),
        )
        val versions: MutableList<CLVersion> = mutableListOf(initialVersion)
        val nonMergedVersions = mutableSetOf<CLVersion>()
        var headVersion: CLVersion = initialVersion
        val createdNodes: MutableSet<String> = Collections.synchronizedSet(TreeSet<String>())

        fun mergeVersion(versionToMerge: CLVersion) {
            headVersion = VersionMerger(initialTree.store, idGenerator).mergeChange(headVersion, versionToMerge)
            nonMergedVersions.remove(versionToMerge)
        }

        for (i in 1..100) {
            val baseVersion = versions[rand.nextInt(versions.size)]
            val branch = OTBranch(PBranch(baseVersion.tree, idGenerator), idGenerator, initialTree.store)
            branch.runWriteT { t ->
                createdNodes += t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?).toString(16)
            }
            val (ops, newTree) = branch.getPendingChanges()
            val newVersion = CLVersion.createRegularVersion(
                idGenerator.generate(),
                null,
                null,
                newTree as CLTree,
                baseVersion,
                ops.map { it.getOriginalOp() }.toTypedArray(),
            )
            versions += newVersion
            nonMergedVersions -= baseVersion
            nonMergedVersions += newVersion

            while (nonMergedVersions.size > rand.nextInt(10)) {
                mergeVersion(nonMergedVersions.random(rand))
            }
        }

        while (nonMergedVersions.isNotEmpty()) {
            mergeVersion(nonMergedVersions.random(rand))
        }

        val headChildren = getChildren(PBranch(headVersion.getTree(), IdGeneratorDummy()))

        assertEquals(createdNodes, headChildren)
    }

    @Ignore
    @Test
    fun linearHistoryPerformance() {
        val idGenerator = IdGenerator.getInstance(100)
        val initialTree = ModelFacade.newLocalTree() as CLTree
        fun version(id: Long, base: CLVersion?): CLVersion {
            return CLVersion.createRegularVersion(
                id,
                null,
                null,
                initialTree,
                base,
                emptyArray(),
            )
        }

        fun merge(id: Long, base: CLVersion, v1: CLVersion, v2: CLVersion): CLVersion {
            return CLVersion.createAutoMerge(
                id,
                initialTree,
                base,
                v1,
                v2,
                emptyArray<IOperation>(),
                initialTree.store,
            )
        }

        val v30000003a = version(12884901946, null)
        val v1000004d1 = version(4294968529, v30000003a)
        val v1000004d3 = version(4294968531, v1000004d1)
        val v200000353 = version(8589935443, v1000004d3)
        val v1000004d5 = version(4294968533, v1000004d3)
        val v30000003c = merge(12884901948, v1000004d3, v200000353, v1000004d5)
        val v1000004d6 = merge(4294968534, v1000004d3, v1000004d5, v200000353)
        val v30000003d = merge(12884901949, v1000004d3, v30000003c, v1000004d6)
        val v30000003e = merge(12884901950, v1000004d3, v30000003d, v1000004d6)
        val v200000354 = merge(8589935444, v1000004d3, v200000353, v30000003d)
        val v30000003f = merge(12884901951, v1000004d3, v30000003e, v200000354)
        val v300000040 = merge(12884901952, v1000004d3, v30000003f, v200000354)
        val v200000356 = version(8589935446, v200000354)
        val v300000041 = merge(12884901953, v1000004d3, v300000040, v200000356)
        val v1000004d8 = version(4294968536, v1000004d6)
        val v300000042 = merge(12884901954, v1000004d3, v300000041, v1000004d8)
        val v1000004d9 = merge(4294968537, v1000004d3, v1000004d8, v300000041)
        val v300000043 = merge(12884901955, v1000004d3, v300000042, v1000004d9)
        val v300000044 = merge(12884901956, v1000004d3, v300000043, v1000004d9)
        val v200000357 = merge(8589935447, v1000004d3, v200000356, v300000041)
        val v300000045 = merge(12884901957, v1000004d3, v300000044, v200000357)
        val v300000046 = merge(12884901958, v1000004d3, v300000045, v200000357)
        val v1000004da = merge(4294968538, v1000004d3, v1000004d9, v300000046)
        val v300000047 = merge(12884901959, v1000004d3, v300000046, v1000004da)
        val v300000048 = merge(12884901960, v1000004d3, v300000047, v1000004da)
        val v200000359 = version(8589935449, v200000357)
        val v300000049 = merge(12884901961, v1000004d3, v300000048, v200000359)
        val v1000004dc = version(4294968540, v1000004da)
        val v30000004a = merge(12884901962, v1000004d3, v300000049, v1000004dc)
        val v20000035a = merge(8589935450, v1000004d3, v200000359, v300000046)
        val v30000004b = merge(12884901963, v1000004d3, v30000004a, v20000035a)
        val v30000004c = merge(12884901964, v1000004d3, v30000004b, v20000035a)
        val v1000004dd = merge(4294968541, v1000004d3, v1000004dc, v30000004c)
        val v30000004d = merge(12884901965, v1000004d3, v30000004c, v1000004dd)
        val v30000004e = merge(12884901966, v1000004d3, v30000004d, v1000004dd)
        val v20000035b = merge(8589935451, v1000004d3, v20000035a, v30000004c)
        val v30000004f = merge(12884901967, v1000004d3, v30000004e, v20000035b)
        val v300000050 = merge(12884901968, v1000004d3, v30000004f, v20000035b)
        val v1000004df = version(4294968543, v1000004dd)
        val v300000051 = merge(12884901969, v1000004d3, v300000050, v1000004df)
        val v20000035d = version(8589935453, v20000035b)
        val v300000052 = merge(12884901970, v1000004d3, v300000051, v20000035d)
        val v1000004e0 = merge(4294968544, v1000004d3, v1000004df, v300000051)
        val v300000053 = merge(12884901971, v1000004d3, v300000052, v1000004e0)
        val v300000054 = merge(12884901972, v1000004d3, v300000053, v1000004e0)
        val v20000035f = version(8589935455, v20000035d)
        val v300000055 = merge(12884901973, v1000004d3, v300000054, v20000035f)
        val v200000360 = merge(8589935456, v1000004d3, v20000035f, v300000052)
        val v300000056 = merge(12884901974, v1000004d3, v300000055, v200000360)
        val v300000057 = merge(12884901975, v1000004d3, v300000056, v200000360)
        val v1000004e2 = version(4294968546, v1000004e0)
        val v300000058 = merge(12884901976, v1000004d3, v300000057, v1000004e2)
        val v200000362 = version(8589935458, v200000360)
        val v300000059 = merge(12884901977, v1000004d3, v300000058, v200000362)
        val v1000004e3 = merge(4294968547, v1000004d3, v1000004e2, v300000058)
        val v30000005a = merge(12884901978, v1000004d3, v300000059, v1000004e3)
        val v30000005b = merge(12884901979, v1000004d3, v30000005a, v1000004e3)
        val v1000004e5 = version(4294968549, v1000004e3)
        val v30000005c = merge(12884901980, v1000004d3, v30000005b, v1000004e5)
        val v200000363 = merge(8589935459, v1000004d3, v200000362, v300000058)
        val v30000005d = merge(12884901981, v1000004d3, v30000005c, v200000363)
        val v30000005e = merge(12884901982, v1000004d3, v30000005d, v200000363)
        val v200000365 = version(8589935461, v200000363)
        val v30000005f = merge(12884901983, v1000004d3, v30000005e, v200000365)
        val v1000004e7 = version(4294968551, v1000004e5)
        val v300000060 = merge(12884901984, v1000004d3, v30000005f, v1000004e7)
        val v200000367 = version(8589935463, v200000365)
        val v300000061 = merge(12884901985, v1000004d3, v300000060, v200000367)
        val v1000004e9 = version(4294968553, v1000004e7)
        val v300000062 = merge(12884901986, v1000004d3, v300000061, v1000004e9)
        val v1000004ea = merge(4294968554, v1000004d3, v1000004e9, v300000060)
        val v300000063 = merge(12884901987, v1000004d3, v300000062, v1000004ea)
        val v300000064 = merge(12884901988, v1000004d3, v300000063, v1000004ea)
        val v200000369 = version(8589935465, v200000367)
        val v300000065 = merge(12884901989, v1000004d3, v300000064, v200000369)
        val v20000036a = merge(8589935466, v1000004d3, v200000369, v300000060)
        val v300000066 = merge(12884901990, v1000004d3, v300000065, v20000036a)
        val v1000004eb = merge(4294968555, v1000004d3, v1000004ea, v300000061)
        val v300000067 = merge(12884901991, v1000004d3, v300000066, v1000004eb)
        val v20000036c = version(8589935468, v20000036a)
        val v300000068 = merge(12884901992, v1000004d3, v300000067, v20000036c)
        val v300000069 = merge(12884901993, v1000004d3, v300000068, v20000036a)
        val v30000006b = merge(12884901995, v1000004d3, v300000069, v1000004eb)
        val v20000036d = merge(8589935469, v1000004d3, v20000036c, v300000063)
        val v30000006c = merge(12884901996, v1000004d3, v30000006b, v20000036d)
        val v30000006d = merge(12884901997, v1000004d3, v30000006c, v20000036d)
        val v1000004ec = merge(4294968556, v1000004d3, v1000004eb, v30000006c)
        val v30000006e = merge(12884901998, v1000004d3, v30000006d, v1000004ec)
        val v300000070 = merge(12884902000, v1000004d3, v30000006e, v1000004ec)
        val v20000036e = merge(8589935470, v1000004d3, v20000036d, v30000006e)
        val v300000071 = merge(12884902001, v1000004d3, v300000070, v20000036e)
        val v1000004ed = merge(4294968557, v1000004d3, v1000004ec, v30000006e)
        val v300000072 = merge(12884902002, v1000004d3, v300000071, v1000004ed)
        val v20000036f = merge(8589935471, v1000004d3, v20000036e, v30000006e)
        val v300000073 = merge(12884902003, v1000004d3, v300000072, v20000036f)
        val v300000074 = merge(12884902004, v1000004d3, v300000073, v20000036f)
        val v300000075 = merge(12884902005, v1000004d3, v300000074, v20000036e)
        val v1000004ee = merge(4294968558, v1000004d3, v30000006e, v300000075)
        LinearHistory(v1000004d3.getContentHash()).load(v300000075, v1000004ee)
    }

    private fun getChildren(branch: IBranch): SortedSet<String> {
        return branch.computeRead {
            branch.getRootNode().allChildren.map { (it as PNodeAdapter).nodeId.toString(16) }.toSortedSet()
        }
    }
}

private fun IBranch.treeHash(): String = computeReadT { t -> (t.tree as CLTree).hash }
