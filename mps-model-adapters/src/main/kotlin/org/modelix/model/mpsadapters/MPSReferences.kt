package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.SNodePointer
import jetbrains.mps.util.StringUtil
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.jetbrains.mps.openapi.project.Project
import org.modelix.model.api.INodeReference
import org.modelix.mps.multiplatform.model.MPSModelReference
import org.modelix.mps.multiplatform.model.MPSModuleReference
import org.modelix.mps.multiplatform.model.MPSNodeReference

fun SModuleReference.toModelix() = MPSModuleReference(moduleId.toString())
fun MPSModuleReference.toMPS(): SModuleReference = PersistenceFacade.getInstance().createModuleReference(
    PersistenceFacade.getInstance().createModuleId(moduleId),
    null,
)

fun SModelReference.toModelix() = MPSModelReference(moduleReference?.toModelix(), modelId.toString())
fun MPSModelReference.toMPS(): SModelReference = PersistenceFacade.getInstance().createModelReference(
    moduleReference?.toMPS(),
    PersistenceFacade.getInstance().createModelId(modelId),
    SModelName("unknown"), // we need a non-empty model name here to avoid PersistenceFacade$IncorrectModelReferenceFormatException: Incomplete model reference, the presentation part is absent
)

fun SNodeReference.toModelix() = MPSNodeReference(
    modelReference?.toModelix(),
    nodeId.toString(),
)
fun MPSNodeReference.toMPS() = SNodePointer(modelReference?.toMPS(), PersistenceFacade.getInstance().createNodeId(nodeId))

fun SNodeReference.withoutNames(): String {
    val modelPrefix = modelReference?.let { it.withoutNames() + "/" } ?: ""
    return modelPrefix + StringUtil.escapeRefChars(nodeId?.toString() ?: "")
}

fun SModelReference.withoutNames(): String {
    // MPS often omits the module reference, if the model ID is globally unique.
    // The module reference is ignored here, even if one is provided, to generate a consistent ID.

    val modulePrefix = if (modelId.isGloballyUnique) {
        ""
    } else {
        moduleReference?.let { it.withoutNames() + "/" } ?: ""
    }
    return modulePrefix + StringUtil.escapeRefChars(modelId.toString())
}

fun SModuleReference.withoutNames(): String {
    return StringUtil.escapeRefChars(moduleId.toString())
}

data class MPSDevKitDependencyReference(
    val usedModuleId: SModuleId,
    val userModule: SModuleReference? = null,
    val userModel: SModelReference? = null,
) : INodeReference() {

    companion object {
        internal const val PREFIX = "mps-devkit"
        internal const val SEPARATOR = "#IN#"
    }

    override fun serialize(): String {
        val importer = userModule?.let { "mps-module:${it.withoutNames()}" }
            ?: userModel?.let { "mps-model:${it.withoutNames()}" }
            ?: error("importer not found")

        return "$PREFIX:$usedModuleId$SEPARATOR$importer"
    }
}

data class MPSJavaModuleFacetReference(val moduleReference: SModuleReference) : INodeReference() {

    companion object {
        internal const val PREFIX = "mps-java-facet"
    }

    override fun serialize(): String {
        return "$PREFIX:${moduleReference.withoutNames()}"
    }
}

data class MPSModelImportReference(
    val importedModel: SModelReference,
    val importingModel: SModelReference,
) : INodeReference() {

    companion object {
        internal const val PREFIX = "mps-model-import"
        internal const val SEPARATOR = "#IN#"
    }

    override fun serialize(): String {
        return "$PREFIX:${importedModel.withoutNames()}$SEPARATOR${importingModel.withoutNames()}"
    }
}

data class MPSModuleDependencyReference(
    val usedModuleId: SModuleId,
    val userModuleReference: SModuleReference,
) : INodeReference() {

    companion object {
        internal const val PREFIX = "mps-module-dep"
        internal const val SEPARATOR = "#IN#"
    }

    override fun serialize(): String {
        return "$PREFIX:$usedModuleId$SEPARATOR${userModuleReference.withoutNames()}"
    }
}

data class MPSProjectReference(val projectName: String) : INodeReference() {

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

data class MPSProjectModuleReference(val moduleRef: SModuleReference, val projectRef: MPSProjectReference) : INodeReference() {

    companion object {
        internal const val PREFIX = "mps-project-module"
        internal const val SEPARATOR = "#IN#"
    }

    override fun serialize(): String {
        return "$PREFIX:${moduleRef.withoutNames()}$SEPARATOR${projectRef.serialize()}"
    }
}

data class MPSSingleLanguageDependencyReference(
    val usedModuleId: SModuleId,
    val userModule: SModuleReference? = null,
    val userModel: SModelReference? = null,
) : INodeReference() {

    companion object {
        internal const val PREFIX = "mps-lang"
        internal const val SEPARATOR = "#IN#"
    }

    override fun serialize(): String {
        val importer = userModule?.let { "mps-module:${it.withoutNames()}" }
            ?: userModel?.let { "mps-model:${it.withoutNames()}" }
            ?: error("importer not found")

        return "$PREFIX:$usedModuleId$SEPARATOR$importer"
    }
}

object MPSRepositoryReference : INodeReference() {
    internal const val PREFIX = "mps-repository"

    override fun serialize(): String {
        return "$PREFIX:repository"
    }
}
