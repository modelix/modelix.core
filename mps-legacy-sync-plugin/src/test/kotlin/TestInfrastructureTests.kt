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
import org.jetbrains.mps.openapi.module.SModule

/**
 * Doesn't test the SyncService itself, but only if we use the IntelliJ test infrastructure correctly.
 */
class TestInfrastructureTests : SyncPluginTestBase(null) {

//    fun testEstablishConnection() = runTestWithSyncService { syncService ->
//        val modelClient = syncService.syncService.getAllClients().single()
//        TestCase.assertNotSame(0, modelClient.getClientId())
//    }

    fun testGlobalModulesLoaded() {
        val repository = mpsProject.repository
        lateinit var modules: List<SModule>
        repository.modelAccess.runReadAction {
            modules = repository.modules.toList()
        }
        TestCase.assertNotSame(0, modules.size)
    }

    fun testProjectModulesLoaded_with_SimpleProjectA() {
        val repository = mpsProject.repository
        lateinit var modules: List<SModule>
        repository.modelAccess.runReadAction {
            modules = mpsProject.projectModules.toList()
        }
        TestCase.assertEquals(1, modules.size)
        val module = modules.single()
        TestCase.assertEquals("simple.solution1", module.moduleName)
    }
}
