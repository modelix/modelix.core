package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.SNodePointer
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.project.Project
import org.modelix.model.api.INodeReference

data class MPSModuleReference(val moduleReference: SModuleReference) : INodeReference {

    companion object {
        internal const val PREFIX = "mps-module"
    }

    override fun serialize(): String {
        return "$PREFIX:$moduleReference"
    }
}

data class MPSModelReference(val modelReference: SModelReference) : INodeReference {

    companion object {
        internal const val PREFIX = "mps-model"
    }

    override fun serialize(): String {
        return "$PREFIX:$modelReference"
    }
}

data class MPSNodeReference(val ref: SNodeReference) : INodeReference {
    companion object {

        internal const val PREFIX = "mps"

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
        return "$PREFIX:$ref"
    }
}

data class MPSDevKitDependencyReference(
    val usedModuleId: SModuleId,
    val userModule: SModuleReference? = null,
    val userModel: SModelReference? = null,
) : INodeReference {

    companion object {
        internal const val PREFIX = "mps-devkit"
        internal const val SEPARATOR = "#IN#"
    }

    override fun serialize(): String {
        val importer = userModule?.let { "mps-module:$it" }
            ?: userModel?.let { "mps-model:$it" }
            ?: error("importer not found")

        return "$PREFIX:$usedModuleId$SEPARATOR$importer"
    }
}

data class MPSJavaModuleFacetReference(val moduleReference: SModuleReference) : INodeReference {

    companion object {
        internal const val PREFIX = "mps-java-facet"
    }

    override fun serialize(): String {
        return "$PREFIX:$moduleReference"
    }
}

data class MPSModelImportReference(
    val importedModel: SModelReference,
    val importingModel: SModelReference,
) : INodeReference {

    companion object {
        internal const val PREFIX = "mps-model-import"
        internal const val SEPARATOR = "#IN#"
    }

    override fun serialize(): String {
        return "$PREFIX:$importedModel$SEPARATOR$importingModel"
    }
}

data class MPSModuleDependencyReference(
    val usedModuleId: SModuleId,
    val userModuleReference: SModuleReference,
) : INodeReference {

    companion object {
        internal const val PREFIX = "mps-module-dep"
        internal const val SEPARATOR = "#IN#"
    }

    override fun serialize(): String {
        return "$PREFIX:$usedModuleId$SEPARATOR$userModuleReference"
    }
}

data class MPSProjectReference(val projectName: String) : INodeReference {

    companion object {
        internal const val PREFIX = "mps-project"
        internal const val PREFIX_COLON = "$PREFIX:"

        fun tryConvert(ref: INodeReference): MPSProjectReference? {
            if (ref is MPSProjectReference) return ref
            val serialized = ref.serialize()
            return if (serialized.startsWith(PREFIX_COLON)) {
                MPSProjectReference(serialized.substringAfter(PREFIX_COLON))
            } else {
                null
            }
        }
    }

    constructor(project: Project) : this(project.readName())

    override fun serialize(): String {
        return "$PREFIX:$projectName"
    }
}

data class MPSProjectModuleReference(val moduleRef: SModuleReference, val projectRef: MPSProjectReference) : INodeReference {

    companion object {
        internal const val PREFIX = "mps-project-module"
        internal const val SEPARATOR = "#IN#"
    }

    override fun serialize(): String {
        return "$PREFIX:$moduleRef$SEPARATOR${projectRef.serialize()}"
    }
}

data class MPSSingleLanguageDependencyReference(
    val usedModuleId: SModuleId,
    val userModule: SModuleReference? = null,
    val userModel: SModelReference? = null,
) : INodeReference {

    companion object {
        internal const val PREFIX = "mps-lang"
        internal const val SEPARATOR = "#IN#"
    }

    override fun serialize(): String {
        val importer = userModule?.let { "mps-module:$it" }
            ?: userModel?.let { "mps-model:$it" }
            ?: error("importer not found")

        return "$PREFIX:$usedModuleId$SEPARATOR$importer"
    }
}

object MPSRepositoryReference : INodeReference {
    internal const val PREFIX = "mps-repository"

    override fun serialize(): String {
        return "$PREFIX:repository"
    }
}
