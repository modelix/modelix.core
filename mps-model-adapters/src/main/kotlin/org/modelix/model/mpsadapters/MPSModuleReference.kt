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

package org.modelix.model.mpsadapters

import jetbrains.mps.project.structure.modules.ModuleReference
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.INodeReferenceSerializerEx
import kotlin.reflect.KClass

data class MPSModuleReference(val moduleReference: SModuleReference) : INodeReference {
    companion object {
        init {
            INodeReferenceSerializer.register(MPSModuleReferenceSerializer)
        }
    }
}

object MPSModuleReferenceSerializer : INodeReferenceSerializerEx {
    override val prefix = "mps-module"
    override val supportedReferenceClasses: Set<KClass<out INodeReference>> = setOf(MPSModuleReference::class)

    override fun serialize(ref: INodeReference): String {
        return (ref as MPSModuleReference).moduleReference.toString()
    }

    override fun deserialize(serialized: String): INodeReference {
        return MPSModuleReference(ModuleReference.parseReference(serialized))
    }
}
