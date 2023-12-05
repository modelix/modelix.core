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
import jetbrains.mps.smodel.SModelId
import junit.framework.TestCase
import org.modelix.model.api.IChildLink
import org.modelix.model.data.NodeData
import org.modelix.model.lazy.BranchReference
import org.modelix.model.mpsplugin.SModuleUtils
import java.util.UUID

class ModuleCanBeCopiedOnAndSyncedCloudTest : SyncPluginTestBase("SimpleProjectF") {

    fun testInitialSyncToServer() = runTestWithSyncService { syncService ->

        // check that the MPS project is loaded correctly from disk
        TestCase.assertEquals(mpsProject.projectModules.size, projectAsNode.getChildren(IChildLink.fromName("projectModules")).count())
        val existingSolution = mpsProject.projectModules.single()
        assertEquals("simple.solution1", existingSolution.moduleName)

        // initial sync to the server
        val moduleBinding = syncService.connectServer(httpClient, Url("http://localhost/"))
            .newBranchConnection(defaultBranchRef)
            .bindModule(existingSolution, null)
        moduleBinding.flush()
        compareDumps(useInitialDump = true)

        // create new model inside the existing solution
        assertEquals(1, readAction { SModuleUtils.getModelsWithoutDescriptor(existingSolution).size })
        writeAction {
            SModuleUtils.createModel(existingSolution, "my.wonderful.brandnew.modelInExistingModule", SModelId.regular(UUID.fromString("1c22f2f9-f1f3-45f8-8f4b-69b248928af5")))
        }
        assertEquals(2, readAction { SModuleUtils.getModelsWithoutDescriptor(existingSolution).size })
        moduleBinding.flush()
        assertEquals(2, readAction { SModuleUtils.getModelsWithoutDescriptor(existingSolution).size })
        compareDumps()

        // remove initially existing module from MPS
        // TODO delete module on the server and disable the binding
//        assertEquals(1, readAction { mpsProject.projectModules.size })
//        writeAction {
//            mpsProject.removeModule(existingSolution)
//            assertEquals(0, readAction { mpsProject.projectModules.size })
//        }
//        assertEquals(0, readAction { mpsProject.projectModules.size })
//        moduleBinding.flush()
//        compareDumps()

        // TODO test what happens when .bindModule and .bindProject are used at the same time. The module binding should then be disabled.
    }

    override fun readDumpFromMPS(): NodeData {
        return super.readDumpFromMPS().children.single().copy(role = null)
    }

    protected override suspend fun readDumpFromServer(branchRef: BranchReference): NodeData {
        return super.readDumpFromServer(branchRef)
            .children.single() // the project node
            .copy(id = null, role = null)
    }
}
