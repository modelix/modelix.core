package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.adapter.structure.concept.SConceptAdapterById
import jetbrains.mps.smodel.adapter.structure.concept.SInterfaceConceptAdapterById
import org.modelix.model.api.SimpleChildLink
import org.modelix.model.api.SimpleConcept
import org.modelix.model.api.SimpleLanguage
import org.modelix.model.mpsadapters.RepositoryLanguage.BaseConcept
import org.modelix.model.mpsadapters.RepositoryLanguage.INamedConcept
import org.modelix.model.mpsadapters.RepositoryLanguage.addConcept

@Deprecated("use org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts", ReplaceWith("org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts"))
object RepositoryLanguage : SimpleLanguage("org.modelix.model.repositoryconcepts", uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80") {
    val BaseConcept = MPSConcept(SConceptAdapterById.deserialize("c:ceab5195-25ea-4f22-9b92-103b95ca8c0c/1133920641626:jetbrains.mps.lang.core.structure.BaseConcept"))
    val INamedConcept = MPSConcept(SInterfaceConceptAdapterById.deserialize("i:ceab5195-25ea-4f22-9b92-103b95ca8c0c/1169194658468:jetbrains.mps.lang.core.structure.INamedConcept"))
    val NamePropertyUID = "ceab5195-25ea-4f22-9b92-103b95ca8c0c/1169194658468/1169194664001"
    val VirtualPackagePropertyUID = "ceab5195-25ea-4f22-9b92-103b95ca8c0c/1133920641626/1193676396447"

    val Model = org.modelix.model.mpsadapters.Model
    val Module = org.modelix.model.mpsadapters.Module
    val Repository = org.modelix.model.mpsadapters.Repository
}

object Model : SimpleConcept(
    conceptName = "Model",
    uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892",
    directSuperConcepts = listOf(BaseConcept, INamedConcept),
) {
    init { addConcept(this) }
    val rootNodes = SimpleChildLink(
        simpleName = "rootNodes",
        isMultiple = true,
        isOptional = true,
        targetConcept = BaseConcept,
        uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/474657388638618900",
    )
}

object Module : SimpleConcept(
    conceptName = "Module",
    uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895",
    directSuperConcepts = listOf(BaseConcept, INamedConcept),
) {
    init { addConcept(this) }
    val models = SimpleChildLink(
        simpleName = "models",
        isMultiple = true,
        isOptional = true,
        targetConcept = Model,
        uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/474657388638618898",
    )
}

object Repository : SimpleConcept(
    conceptName = "Repository",
    uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902",
    directSuperConcepts = listOf(BaseConcept),
) {
    init { addConcept(this) }
    val modules = SimpleChildLink(
        simpleName = "modules",
        isMultiple = true,
        isOptional = true,
        targetConcept = Module,
        uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/474657388638618903",
    )
}
