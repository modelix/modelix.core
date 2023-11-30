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
import org.modelix.model.api.IChildLink
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.mps.MPSLanguageRepository
import org.modelix.model.mpsadapters.mps.SModuleAsNode
import org.modelix.model.mpsadapters.plugin.MPSNodeReferenceSerializer

class ProjectCanBeCopiedAndSyncOnCloudTest : SyncPluginTestBase("SimpleProjectF") {

    fun testInitialSyncToServer() = runTestWithSyncService { syncService ->
        ILanguageRepository.register(MPSLanguageRepository.INSTANCE)
        INodeReferenceSerializer.register(MPSNodeReferenceSerializer.INSTANCE)

        val json = Json { prettyPrint = true }

        val mpsProject = getMPSProject()
        val projectAsNode = org.modelix.model.mpsadapters.mps.ProjectAsNode(mpsProject) // org.modelix.model.mpsadapters.MPSProjectAsNode(mpsProject)

        TestCase.assertEquals(mpsProject.projectModules.size, projectAsNode.getChildren(IChildLink.fromName("projectModules")).count())

        val dumpFromMPS = projectAsNode.getArea().executeRead {
            projectAsNode.asData(includeChildren = false)
                .copy(
                    id = null,
                    role = "",
                    children = mpsProject.projectModules.map { SModuleAsNode(it).asData() },
                )
        }

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
            .copy(id = null, role = "")
        TestCase.assertEquals(json.encodeToString(dumpFromMPS.normalize()), json.encodeToString(dumpFromServer.normalize()))
    }
}

private fun NodeData.normalize(): NodeData {
    val idMap = HashMap<String, String>()
    collectNodeIds(this, idMap)
    return normalizeNodeData(this, idMap)
}
private fun normalizeNodeData(node: NodeData, originalIds: MutableMap<String, String>): NodeData {
    var filteredChildren = node.children
    if (node.concept == "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895") { // Module
        // TODO remove this filter and fix the test
        filteredChildren = filteredChildren.filter { it.role == "models" }
    }
    var ignoreId = false
    if (node.concept == "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242429") { // SingleLanguageDependency
        // TODO remove this filter and fix the test
        ignoreId = true
    }

    return node.copy(
        id = (originalIds[node.id] ?: node.id).takeIf { !ignoreId },
        properties = node.properties.minus(NodeData.ID_PROPERTY_KEY).minus(NodeData.ORIGINAL_NODE_ID_KEY).toSortedMap(),
        references = node.references.mapValues { originalIds[it.value] ?: it.value }.toSortedMap(),
        children = filteredChildren.map { normalizeNodeData(it, originalIds) }.sortedBy { it.role },
    )
}

private fun collectNodeIds(node: NodeData, idMap: MutableMap<String, String>) {
    val copyId = node.id
    val originalId = node.properties[NodeData.ORIGINAL_NODE_ID_KEY]
    if (originalId != null && copyId != null) {
        idMap[copyId] = originalId
    }
    node.children.forEach { collectNodeIds(it, idMap) }
}
