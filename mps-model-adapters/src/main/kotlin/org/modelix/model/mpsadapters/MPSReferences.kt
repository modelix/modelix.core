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

import jetbrains.mps.smodel.SNodePointer
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.model.api.INodeReference

data class MPSModuleReference(val moduleReference: SModuleReference) : INodeReference {
    override fun serialize(): String {
        return "mps-module:$moduleReference"
    }
}

data class MPSModelReference(val modelReference: SModelReference) : INodeReference {
    override fun serialize(): String {
        return "mps-model:$modelReference"
    }
}

data class MPSNodeReference(val ref: SNodeReference) : INodeReference {
    companion object {
        @Deprecated("INodeResolutionScope.resolveNode(INodeReference) is now responsible for deserializing supported references")
        fun tryConvert(ref: INodeReference): MPSNodeReference? {
            if (ref is MPSNodeReference) return ref
            val serialized = ref.serialize()
            val serializedMPSRef = when {
                serialized.startsWith("mps-node:") -> serialized.substringAfter("mps-node:")
                serialized.startsWith("mps:") -> serialized.substringAfter("mps:")
                else -> return null
            }
            return MPSNodeReference(SNodePointer.deserialize(serializedMPSRef))
        }
    }

    override fun serialize(): String {
        return "mps:$ref"
    }
}

data class MPSDevKitDependencyReference(
    val usedModuleId: SModuleId,
    val userModule: SModuleReference? = null,
    val userModel: SModelReference? = null,
) : INodeReference {
    override fun serialize(): String {
        val importer = userModule?.let { "mps-module:$it" }
            ?: userModel?.let { "mps-model:$it" }
            ?: throw IllegalStateException("importer not found")

        return "mps-devkit:$usedModuleId#IN#$importer"
    }
}

data class MPSJavaModuleFacetReference(val moduleReference: SModuleReference) : INodeReference {
    override fun serialize(): String {
        return "mps-java-facet:$moduleReference"
    }
}

data class MPSModelImportReference(
    val importedModel: SModelReference,
    val importingModel: SModelReference,
) : INodeReference {
    override fun serialize(): String {
        return "mps-model-import:$importedModel#IN#$importingModel"
    }
}

data class MPSModuleDependencyReference(
    val usedModuleId: SModuleId,
    val userModuleReference: SModuleReference,
) : INodeReference {
    override fun serialize(): String {
        return "mps-module-dependency:$usedModuleId#IN#$userModuleReference"
    }
}

data class MPSProjectReference(val projectName: String) : INodeReference {
    override fun serialize(): String {
        return "mps-project:$projectName"
    }
}

data class MPSProjectModuleReference(val moduleRef: SModuleReference, val projectRef: MPSProjectReference) : INodeReference {
    override fun serialize(): String {
        return "mps-project-module:$moduleRef#IN#${projectRef.serialize()}"
    }
}

data class MPSSingleLanguageDependencyReference(
    val usedModuleId: SModuleId,
    val userModule: SModuleReference? = null,
    val userModel: SModelReference? = null,
) : INodeReference {
    override fun serialize(): String {
        val importer = userModule?.let { "mps-module:$it" }
            ?: userModel?.let { "mps-model:$it" }
            ?: throw IllegalStateException("importer not found")

        return "mps-lang:$usedModuleId#IN#$importer"
    }
}
