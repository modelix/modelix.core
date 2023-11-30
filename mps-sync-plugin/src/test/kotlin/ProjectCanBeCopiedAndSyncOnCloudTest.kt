/*
 * Copyright (c) 2023.
 *
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

import io.ktor.http.Url
import junit.framework.TestCase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSProjectAsNode

class ProjectCanBeCopiedAndSyncOnCloudTest : SyncPluginTestBase("SimpleProjectF") {

    fun testInitialSyncToServer() = runTestWithSyncService { syncService ->
        val json = Json { prettyPrint = true }

        val mpsProject = getMPSProject()
        val dumpFromMPS: NodeData = MPSProjectAsNode(mpsProject).asData()
            .copy(role = "")
        json.encodeToString(dumpFromMPS).lineSequence().forEach { println("MPS   : $it") }

//        TestCase.assertEquals("SimpleProjectF", mpsProject.name)
//        TestCase.assertEquals(0, syncService.getBindingList().size)
        val module = mpsProject.projectModules.single()
        TestCase.assertEquals("simple.solution1", module.moduleName)
        val branchRef = RepositoryId("default").getBranchReference()

        syncService.connectServer(httpClient, Url("http://localhost/"))
            .newBranchConnection(branchRef)
            .bindProject(mpsProject, null)
            .flush()

        val dumpFromServer = runWithNewConnection { client ->
            val versionOnServer = client.pull(branchRef, null)
            versionOnServer.getTree().asData()
        }
            .root // the node with ID ITree.ROOT_ID
            .children.single() // the project node
            .copy(role = "")
        json.encodeToString(dumpFromServer).lineSequence().forEach { println("Server: $it") }
        TestCase.assertEquals(json.encodeToString(dumpFromMPS), json.encodeToString(dumpFromServer))
    }
}
