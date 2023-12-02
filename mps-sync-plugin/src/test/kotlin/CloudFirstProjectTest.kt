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
import kotlinx.serialization.encodeToString
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ITree
import org.modelix.model.api.key
import org.modelix.model.client2.runWriteOnBranch

// TODO enable and fix this test
abstract class CloudFirstProjectTestUsingRoleIds : CloudFirstProjectTest(true)

class CloudFirstProjectTestUsingRoleNames : CloudFirstProjectTest(false)

abstract class CloudFirstProjectTest(val useRoleIds: Boolean) : SyncPluginTestBase(null) {

    fun testInitialSyncToServer() = runTestWithSyncService { syncService ->
        val initialMpsProjectName = mpsProject.name

        // check that it is a new empty MPS project
        assertEquals(0, mpsProject.projectModules.size)

        // create a new project on the server
        val initialProjectName = "a project name mismatch shouldn't matter"
        val dataForServerInit = buildMPSProjectData(useRoleIds = useRoleIds) {
            name(initialProjectName)
            module {
                name("my.module.a")
            }
        }
        println(json.encodeToString(dataForServerInit))
        val projectNodeIdOnServer = runWithNewConnection { client ->
            client.initRepository(defaultBranchRef.repositoryId, useRoleIds = useRoleIds) // TODO also test with useRoleIds = true
            client.runWriteOnBranch(defaultBranchRef) { branch ->
                dataForServerInit.load(branch.writeTransaction, ITree.ROOT_ID)
            }
        }
        compareDumps(dataForServerInit, readDumpFromServer())

        // bind the project to MPS
        val projectBinding = syncService.connectServer(httpClient, Url("http://localhost/"))
            .newBranchConnection(defaultBranchRef)
            .bindProject(mpsProject, projectNodeIdOnServer)
        projectBinding.flush()
        assertTrue("Project name should not be synchronized", initialProjectName != mpsProject.name)
        compareDumps(
            readDumpFromServer().also {
                // A mismatch of the project name should not be synchronized
                assertEquals(
                    initialProjectName,
                    it.properties[BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.key(useRoleIds)],
                )
            },
            dumpMpsProject().also {
                // A mismatch of the project name should not be synchronized
                assertEquals(
                    initialMpsProjectName,
                    it.properties[BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.key(useRoleIds)],
                )
            },
        )
    }
}
