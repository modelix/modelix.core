package org.modelix.model.mpsadapters

import org.modelix.model.api.BuiltinLanguages

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

class DescriptorModelIsFilteredTest : MpsAdaptersTestBase("SimpleProject") {

    fun `test descriptor model is filtered by adapter`() {
        readAction {
            val module = checkNotNull(mpsProject.projectModules.find { it.moduleName == "Solution1" })

            val descriptorModels = module.models.filter { it.name.stereotype == "descriptor" }
            if (descriptorModels.isEmpty()) return@readAction // they don't seem to exist in MPS 2024.1 anymore

            assertEquals(1, descriptorModels.size)
            assertEquals(2, module.models.count())

            assertEquals(1, MPSModuleAsNode(module).getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).count())
        }
    }
}
