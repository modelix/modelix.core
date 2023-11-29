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

import junit.framework.TestCase
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.data.asData
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSProjectAsNode

@OptIn(UnstableModelixFeature::class)
abstract class ProjectCanBeCopiedAndSyncOnCloudTest : SyncPluginTestBase("SimpleProjectF") {

    fun testInitialSyncToServer() = runTestWithSyncService { syncService ->
        val modelClient = syncService.syncService.getAllClients().single()
        val mpsProject = getMPSProject()
//        TestCase.assertEquals("SimpleProjectF", mpsProject.name)
        TestCase.assertEquals(0, syncService.getBindingList().size)
        val module = mpsProject.projectModules.single()
        TestCase.assertEquals("simple.solution1", module.moduleName)
        val branchRef = RepositoryId("default").getBranchReference()
        val binding = syncService.bindProject(mpsProject, branchRef)
        binding.flush()
        val versionOnServer = modelClient.pull(branchRef, null)
        val dumpFromServer = versionOnServer.getTree().asData()
        val dumpFromMPS = MPSProjectAsNode(mpsProject).asData()
        TestCase.assertEquals(dumpFromMPS, dumpFromServer)
    }


}
