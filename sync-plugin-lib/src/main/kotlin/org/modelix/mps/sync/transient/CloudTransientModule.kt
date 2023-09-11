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

package org.modelix.mps.sync.transient

import jetbrains.mps.extapi.module.TransientSModule
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.structure.modules.ModuleDescriptor
import org.modelix.model.util.pmap.CustomPMap
import org.modelix.model.util.pmap.SmallPMap.Companion.empty
import org.modelix.mps.sync.userobject.IUserObjectContainer
import org.modelix.mps.sync.userobject.UserObjectKey

// status: ready to test
class CloudTransientModule(name: String, id: ModuleId) : AbstractModule(null), IUserObjectContainer, TransientSModule {

    private val myDescriptor: ModuleDescriptor = ModuleDescriptor()
    private var userObjects: CustomPMap<Any, Any> = empty()

    init {
        myDescriptor.id = id
        myDescriptor.namespace = name
        moduleReference = myDescriptor.moduleReference
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getUserObject(key: UserObjectKey): T = userObjects[key] as T

    override fun <T> putUserObject(key: UserObjectKey, value: T) {
        userObjects = userObjects.put(key, value as Any)!!
    }

    override fun getModuleDescriptor(): ModuleDescriptor = myDescriptor

    override fun collectMandatoryFacetTypes(types: Set<String>) {}

    override fun isPackaged() = false

    override fun isReadOnly() = false
}
