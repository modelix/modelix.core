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
import jetbrains.mps.extapi.module.TransientSModule
import kotlinx.serialization.encodeToString
import org.modelix.model.api.ITree
import org.modelix.model.client2.runWriteOnBranch
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.mpsadapters.mps.SModuleAsNode

// TODO enable and fix this test
abstract class ModuleOnTheCloudCanBeCheckoutAsTransientModuleTestUsingRoleIds : ModuleOnTheCloudCanBeCheckoutAsTransientModuleTest(true)

class ModuleOnTheCloudCanBeCheckoutAsTransientModuleTestUsingRoleNames : ModuleOnTheCloudCanBeCheckoutAsTransientModuleTest(false)

abstract class ModuleOnTheCloudCanBeCheckoutAsTransientModuleTest(val useRoleIds: Boolean) : SyncPluginTestBase(null) {

    fun testInitialSyncToServer() = runTestWithSyncService { syncService ->

        val dataForServerInit = ModelData(
            root = NodeData(
                children = listOf(
                    buildMPSModuleData(useRoleIds = useRoleIds) {
                        role("modules")
                        name("my.moduleA")
                        model {
                            name("my.moduleA.plugin")
                        }
                    },
                ),
            ),
        )
        println(json.encodeToString(dataForServerInit))
        val moduleNodeIdOnServer = runWithNewConnection { client ->
            client.initRepository(defaultBranchRef.repositoryId, useRoleIds = useRoleIds)
            client.runWriteOnBranch(defaultBranchRef) { branch ->
                dataForServerInit.load(branch)
                branch.transaction.getAllChildren(ITree.ROOT_ID).single()
            }
        }
        compareDumps(dataForServerInit.root, readDumpFromServer())

        // bind to an MPS transient module
        val moduleBinding = syncService.connectServer(httpClient, Url("http://localhost/"))
            .newBranchConnection(defaultBranchRef)
            .bindTransientModule(moduleNodeIdOnServer)
        moduleBinding.flush()
        compareDumps(dataForServerInit.root, readDumpFromServer()) // ensure data on server is unmodified
        compareDumps()
    }

    protected override fun readDumpFromMPS() = readAction {
        val modules = mpsProject.repository.modules.filterIsInstance<TransientSModule>()
        NodeData(
            children = modules.map {
                SModuleAsNode(it).asData().let { data ->
                    // Transient modules cannot generate or compile java code
                    data.copy(properties = data.properties.minus("compileInMPS"))
                }
            },
        )
    }
}
