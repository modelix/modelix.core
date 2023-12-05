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
import org.jetbrains.mps.openapi.language.SConcept
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IProperty
import org.modelix.model.api.remove
import org.modelix.model.client2.runWrite
import org.modelix.model.data.NodeData
import org.modelix.model.lazy.BranchReference
import org.modelix.model.mpsplugin.MPSProjectUtils
import org.modelix.model.mpsplugin.SModuleUtils
import java.util.UUID

class ProjectCanBeCopiedAndSyncOnCloudTest : SyncPluginTestBase("SimpleProjectF") {

    fun testInitialSyncToServer() = runTestWithSyncService { syncService ->

        // check that the MPS project is loaded correctly from disk
        TestCase.assertEquals(mpsProject.projectModules.size, projectAsNode.getChildren(IChildLink.fromName("projectModules")).count())
        val existingSolution = mpsProject.projectModules.single()
        TestCase.assertEquals("simple.solution1", existingSolution.moduleName)

        // initial sync to the server
        val projectBinding = syncService.connectServer(httpClient, Url("http://localhost/"))
            .newBranchConnection(defaultBranchRef)
            .bindProject(mpsProject, null)
        // TODO A new repository is created on demand in roleNames mode. Also test with an existing repository in roleIds mode.
        projectBinding.flush()
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

        // create new model inside the new solution
        assertEquals(0, readAction { SModuleUtils.getModelsWithoutDescriptor(newSolution).size })
        writeAction {
            SModuleUtils.createModel(newSolution, "my.wonderful.brandnew.modelInNewModule", SModelId.regular(UUID.fromString("8081c614-b145-4cdf-97ff-ce7cf6b979d2")))
        }
        assertEquals(1, readAction { SModuleUtils.getModelsWithoutDescriptor(newSolution).size })
        projectBinding.flush()
        assertEquals(1, readAction { SModuleUtils.getModelsWithoutDescriptor(newSolution).size })
        compareDumps()

        // create new model inside the existing solution
        assertEquals(1, readAction { SModuleUtils.getModelsWithoutDescriptor(existingSolution).size })
        writeAction {
            SModuleUtils.createModel(existingSolution, "my.wonderful.brandnew.modelInExistingModule", SModelId.regular(UUID.fromString("1c22f2f9-f1f3-45f8-8f4b-69b248928af5")))
        }
        assertEquals(2, readAction { SModuleUtils.getModelsWithoutDescriptor(existingSolution).size })
        projectBinding.flush()
        assertEquals(2, readAction { SModuleUtils.getModelsWithoutDescriptor(existingSolution).size })
        compareDumps()

        // remove initially existing module from MPS
        assertEquals(2, readAction { mpsProject.projectModules.size })
        writeAction {
            mpsProject.removeModule(existingSolution)
            assertEquals(1, readAction { mpsProject.projectModules.size })
        }
        assertEquals(1, readAction { mpsProject.projectModules.size })
        projectBinding.flush()
        compareDumps()

        // create new module on server
        runWithNewConnection { client ->
            client.runWrite(defaultBranchRef) { rootNode ->
                val projectNode = rootNode.allChildren.single()
                val solutionNode = projectNode.addNewChild(BuiltinLanguages.MPSRepositoryConcepts.Project.modules, -1, BuiltinLanguages.MPSRepositoryConcepts.Module)
                solutionNode.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, "cloudFirstModule")
                solutionNode.setPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id, "767bb5b9-3d88-4742-b16d-5dda7b67fe5b")
            }
        }
        projectBinding.flush()
        assertEquals(2, readAction { mpsProject.projectModules.size })
        compareDumps()

        // remove module on server
        assertEquals(2, readAction { mpsProject.projectModules.size })
        runWithNewConnection { client ->
            client.runWrite(defaultBranchRef) { rootNode ->
                val projectNode = rootNode.allChildren.single()
                val solutionNode = projectNode.allChildren.first { it.getPropertyValue(IProperty.fromName("name")) == "cloudFirstModule" }
                solutionNode.remove()
            }
        }
        projectBinding.flush()
        assertEquals(1, readAction { mpsProject.projectModules.size })
        compareDumps()
    }

    fun testNewRootNode() = runTestWithProjectBinding { projectBinding ->
        val classConcept = resolveMPSConcept("jetbrains.mps.baseLanguage.ClassConcept")
        val newNodeName = "MyNewlyCreateClass"
        writeAction {
            val model = mpsProject.projectModules.single().modelsWithoutDescriptor().single()
            val newRootNode = model.createNode(classConcept as SConcept)
            newRootNode.setPropertyByName("name", newNodeName)
            model.addRootNode(newRootNode)
        }
        projectBinding.flush()
        compareDumps()
        println(json.encodeToString(readDumpFromServer()))
        assertEquals(
            org.modelix.model.mpsadapters.mps.SConceptAdapter(classConcept).getUID(),
            readDumpFromServer().children // modules
                .flatMap { it.children } // models
                .flatMap { it.children } // root nodes
                .single { it.properties.any { it.value == newNodeName } } // created root node
                .concept,
        )
    }

    protected override suspend fun readDumpFromServer(branchRef: BranchReference): NodeData {
        return super.readDumpFromServer(branchRef)
            .children.single() // the project node
            .copy(id = null, role = null)
    }
}
