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

import com.intellij.testFramework.runInEdtAndGet
import io.ktor.http.Url
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.Solution
import jetbrains.mps.smodel.SModelId
import junit.framework.TestCase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.client2.runWrite
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.mps.MPSLanguageRepository
import org.modelix.model.mpsadapters.mps.SModuleAsNode
import org.modelix.model.mpsadapters.plugin.MPSNodeReferenceSerializer
import org.modelix.model.mpsplugin.MPSProjectUtils
import org.modelix.model.mpsplugin.SModuleUtils
import java.util.UUID

class ProjectCanBeCopiedAndSyncOnCloudTest : SyncPluginTestBase("SimpleProjectF") {

    fun testInitialSyncToServer() = runTestWithSyncService { syncService ->
        ILanguageRepository.register(MPSLanguageRepository.INSTANCE)
        INodeReferenceSerializer.register(MPSNodeReferenceSerializer.INSTANCE)

        val json = Json { prettyPrint = true }

        val mpsProject = getMPSProject()
        val projectAsNode = org.modelix.model.mpsadapters.mps.ProjectAsNode(mpsProject) // org.modelix.model.mpsadapters.MPSProjectAsNode(mpsProject)

        TestCase.assertEquals(mpsProject.projectModules.size, projectAsNode.getChildren(IChildLink.fromName("projectModules")).count())

        fun dumpMpsProject() = readAction {
            projectAsNode.asData(includeChildren = false)
                .copy(
                    id = null,
                    role = "",
                    children = mpsProject.projectModules.map { SModuleAsNode(it).asData() },
                )
        }

        val initialDumpFromMPS = dumpMpsProject()

//        TestCase.assertEquals("SimpleProjectF", mpsProject.name)
//        TestCase.assertEquals(0, syncService.getBindingList().size)
        val module = mpsProject.projectModules.single()
        TestCase.assertEquals("simple.solution1", module.moduleName)
        val branchRef = RepositoryId("default").getBranchReference()

        val projectBinding = syncService.connectServer(httpClient, Url("http://localhost/"))
            .newBranchConnection(branchRef)
            .bindProject(mpsProject, null)
        projectBinding.flush()

        suspend fun readDumpFromServer() = runWithNewConnection { client ->
            val versionOnServer = client.pull(branchRef, null)
            versionOnServer.getTree().asData()
        }
            .root // the node with ID ITree.ROOT_ID
            .children.single() // the project node
            .copy(id = null, role = "")

        suspend fun compareDumps(useInitialDump: Boolean = false) {
            assertEquals(
                json.encodeToString((if (useInitialDump) initialDumpFromMPS else dumpMpsProject()).normalize()),
                json.encodeToString(readDumpFromServer().normalize()),
            )
        }

        compareDumps(useInitialDump = true)

        // create new solution in MPS
        val newSolutionId = UUID.fromString("e8a7cec0-ecbb-4e2f-b9cd-74f510383c39")
        val newSolution = runInEdtAndGet {
            writeAction {
                MPSProjectUtils.createModule(
                    mpsProject,
                    "MyNewModule",
                    ModuleId.regular(newSolutionId),
                    this@ProjectCanBeCopiedAndSyncOnCloudTest,
                ) as Solution
            }
        }
        assertEquals(ModuleId.regular(newSolutionId), newSolution.moduleId)
        assertEquals(2, mpsProject.projectModules.size)
        projectBinding.flush()
        assertEquals(2, mpsProject.projectModules.size)
        compareDumps()

        // create new model
        assertEquals(0, readAction { SModuleUtils.getModelsWithoutDescriptor(newSolution).size })
        val newModel = writeAction {
            SModuleUtils.createModel(newSolution, "my.wonderful.brandnew.modelInNewModule", SModelId.regular(UUID.fromString("8081c614-b145-4cdf-97ff-ce7cf6b979d2")))
        }
        assertEquals(1, readAction { SModuleUtils.getModelsWithoutDescriptor(newSolution).size })
        projectBinding.flush()
        assertEquals(1, readAction { SModuleUtils.getModelsWithoutDescriptor(newSolution).size })
        compareDumps()

        // remove module from MPS
        assertEquals(2, readAction { mpsProject.projectModules.size })
        writeAction {
            mpsProject.removeModule(mpsProject.projectModules.minus(newSolution).first())
            assertEquals(1, readAction { mpsProject.projectModules.size })
        }
        assertEquals(1, readAction { mpsProject.projectModules.size })
        projectBinding.flush()
        compareDumps()

        // create module on server
        runWithNewConnection { client ->
            client.runWrite(branchRef) { rootNode ->
                val projectNode = rootNode.allChildren.single()
                val solutionNode = projectNode.addNewChild(BuiltinLanguages.MPSRepositoryConcepts.Project.modules, -1, BuiltinLanguages.MPSRepositoryConcepts.Module)
                solutionNode.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, "cloudFirstModule")
                solutionNode.setPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id, "767bb5b9-3d88-4742-b16d-5dda7b67fe5b")
            }
        }
        projectBinding.flush()
        assertEquals(2, readAction { mpsProject.projectModules.size })
        compareDumps()
    }
}

private fun NodeData.normalize(): NodeData {
    val idMap = HashMap<String, String>()
    collectNodeIds(this, idMap)
    return normalizeNodeData(this, idMap)
}
private fun normalizeNodeData(node: NodeData, originalIds: MutableMap<String, String>): NodeData {
    var filteredChildren = node.children
    var replacedId = (originalIds[node.id] ?: node.id)
    if (node.concept == "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895") { // Module
        // TODO remove this filter and fix the test
        filteredChildren = filteredChildren.filter { it.role == "models" }

        if (replacedId?.startsWith("mps-module:") == false && node.properties["id"] != null) {
            // TODO the name shouldn't be part of the ID
            replacedId = "mps-module:" + node.properties["id"] + "(" + node.properties["name"] + ")"
        }
    }
    if (node.concept == "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242429") { // SingleLanguageDependency
        // TODO remove this filter and fix the test
        replacedId = null
    }

    return node.copy(
        id = replacedId,
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
