package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.SNodePointer
import jetbrains.mps.util.StringUtil
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.mps.multiplatform.model.MPSModelReference
import org.modelix.mps.multiplatform.model.MPSModuleReference
import org.modelix.mps.multiplatform.model.MPSNodeReference
import org.modelix.mps.multiplatform.model.MPSProjectReference

fun SModuleId.toModelix() = MPSModuleReference(toString())
fun SModuleReference.toModelix() = moduleId.toModelix()
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

fun MPSDevKitDependencyReference(
    usedModuleId: SModuleId,
    userModule: SModuleReference? = null,
    userModel: SModelReference? = null,
) = org.modelix.mps.multiplatform.model.MPSDevKitDependencyReference(usedModuleId.toModelix(), userModule?.toModelix(), userModel?.toModelix())

fun MPSJavaModuleFacetReference(moduleReference: SModuleReference) = org.modelix.mps.multiplatform.model.MPSJavaModuleFacetReference(moduleReference.toModelix())

fun MPSProjectReference(projectName: String) = org.modelix.mps.multiplatform.model.MPSProjectReference(projectName)
fun MPSProjectReference(project: IMPSProject) = MPSProjectReference(project.getName())
fun MPSProjectReference(project: org.jetbrains.mps.openapi.project.Project) = MPSProjectReference(MPSProjectAdapter(project))

fun MPSProjectModuleReference(moduleRef: SModuleReference, projectRef: MPSProjectReference) = org.modelix.mps.multiplatform.model.MPSProjectModuleReference(moduleRef.toModelix(), projectRef)

fun MPSSingleLanguageDependencyReference(
    usedModuleId: SModuleId,
    userModule: SModuleReference? = null,
    userModel: SModelReference? = null,
) = org.modelix.mps.multiplatform.model.MPSSingleLanguageDependencyReference(usedModuleId.toModelix(), userModule?.toModelix(), userModel?.toModelix())
