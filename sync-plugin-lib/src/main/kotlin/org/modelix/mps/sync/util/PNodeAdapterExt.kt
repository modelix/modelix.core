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

package org.modelix.mps.sync.util

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.area.PArea
import java.util.UUID

// status: ready to test

fun PNodeAdapter.createModuleInRepository(name: String) = PArea(this.branch).executeWrite {
    val newModule = this.addNewChild(
        BuiltinLanguages.MPSRepositoryConcepts.Repository.modules,
        -1,
        BuiltinLanguages.MPSRepositoryConcepts.Module,
    )
    newModule.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, name)
    newModule.setPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id, UUID.randomUUID().toString())
    newModule
}

fun PNodeAdapter.createProject(name: String) = PArea(this.branch).executeWrite {
    val newModule = this.addNewChild(
        BuiltinLanguages.MPSRepositoryConcepts.Repository.projects,
        -1,
        BuiltinLanguages.MPSRepositoryConcepts.Project,
    )
    newModule.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, name)
    newModule
}
