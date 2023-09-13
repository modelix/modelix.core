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

import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PropertyFromName
import org.modelix.model.area.PArea
import java.util.UUID

// status: migrated, but needs some bugfixes

fun PNodeAdapter.createModuleInRepository(name: String): INode {
    // TODO check the concept of this node is Repository
    return PArea(this.branch).executeWrite {
        // TODO instead of "modules" it must be link/Repository : modules/.getName()
        // TODO fix parameter. Problem SConceptAdapter.wrap does not exist anymore in modelix...
        // this.addNewChild("modules", -1, SConceptAdapter.wrap(concept/Module/));
        val newModule: INode? = null!!
        // TODO instead of "name" it must be property/Module : name/.getName()
        val nameProperty = PropertyFromName("name")
        newModule!!.setPropertyValue(nameProperty, name)
        // TODO instead of "id" it must be property/Module : id/.getName()
        val idProperty = PropertyFromName("id")
        newModule.setPropertyValue(idProperty, UUID.randomUUID().toString())
        newModule
    }
}

fun PNodeAdapter.createProject(name: String): INode {
    // TODO check the concept of this node is Repository
    return PArea(this.branch).executeWrite {
        // TODO instead of "projects" it must be link/Repository : projects/.getName()
        // TODO fix parameter. Problem SConceptAdapter.wrap does not exist anymore in modelix...
        // this.addNewChild("projects", -1, SConceptAdapter.wrap(concept/Project/));
        val newModule: INode? = null!!
        // TODO instead of "name" it must be property/Module : name/.getName()
        val nameProperty = PropertyFromName("name")
        newModule!!.setPropertyValue(nameProperty, name)
        newModule
    }
}
