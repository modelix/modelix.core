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

package org.modelix.model.api

import kotlin.reflect.KProperty

/**
 * TODO if you add a new Concept to a language, do not forget to add it to the language's included concepts field.
 * Otherwise the concept will not be eagerly added to the Language, when registering the language in the ILanguageRegistry.
 */
object BuiltinLanguages {
    @Suppress("ClassName")
    object jetbrains_mps_lang_core :
        SimpleLanguage(name = "jetbrains.mps.lang.core", uid = "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c") {

        override var includedConcepts = arrayOf(BaseConcept, Attribute, NodeAttribute, INamedConcept)

        object BaseConcept : SimpleConcept(
            conceptName = "BaseConcept",
            is_abstract = true,
            uid = "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/1133920641626",
        ) {
            init {
                addConcept(this)
            }

            val virtualPackage = SimpleProperty(
                "virtualPackage",
                uid = "ceab5195-25ea-4f22-9b92-103b95ca8c0c/1133920641626/1193676396447",
            ).also(this::addProperty)

            val smodelAttribute by childLink("ceab5195-25ea-4f22-9b92-103b95ca8c0c/1133920641626/5169995583184591170").multiple()
                .optional().type { Attribute }
        }

        object Attribute : SimpleConcept(
            conceptName = "Attribute",
            is_abstract = true,
            uid = "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/5169995583184591161",
            directSuperConcepts = listOf(BaseConcept),
        ) {
            init { addConcept(this) }
        }

        object NodeAttribute : SimpleConcept(
            conceptName = "NodeAttribute",
            is_abstract = true,
            uid = "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/3364660638048049748",
            directSuperConcepts = listOf(Attribute),
        ) {
            init { addConcept(this) }
        }

        object INamedConcept : SimpleConcept(conceptName = "INamedConcept") {
            init { addConcept(this) }
            val name = SimpleProperty(
                "name",
                uid = "ceab5195-25ea-4f22-9b92-103b95ca8c0c/1169194658468/1169194664001",
            ).also(this::addProperty)
        }
    }

    /**
     * These concepts are originally defined in
     * https://github.com/JetBrains/MPS-extensions/blob/5d96c3e69192f8902cf9aa7d846d05ccfb65253d/code/model-api/org.modelix.model.repositoryconcepts/models/org.modelix.model.repositoryconcepts.structure.mps ,
     * but to get rid of that dependency, they are redefined here, with their original IDs to stay compatible.
     */
    object MPSRepositoryConcepts :
        SimpleLanguage("org.modelix.model.repositoryconcepts", uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80") {

        override var includedConcepts = arrayOf(
            Model, Module, Solution, Language, DevKit, Repository, Project, ProjectModule, ModuleReference,
            ModelReference, LanguageDependency, SingleLanguageDependency, DevkitDependency, ModuleFacet,
            JavaModuleFacet, ModuleDependency,
        )

        object Model : SimpleConcept(
            conceptName = "Model",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept, jetbrains_mps_lang_core.INamedConcept),
        ) {
            init { addConcept(this) }

            val id = SimpleProperty(
                "id",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/2615330535972958738",
            ).also(this::addProperty)

            val stereotype = SimpleProperty(
                "stereotype",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/3832696962605996173",
            ).also(this::addProperty)

            val rootNodes = SimpleChildLink(
                simpleName = "rootNodes",
                isMultiple = true,
                isOptional = true,
                targetConcept = jetbrains_mps_lang_core.BaseConcept,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/474657388638618900",
            )
            val modelImports = SimpleChildLink(
                simpleName = "modelImports",
                isMultiple = true,
                isOptional = true,
                targetConcept = ModelReference,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/6402965165736931000",
            )
            val usedLanguages = SimpleChildLink(
                simpleName = "usedLanguages",
                isMultiple = true,
                isOptional = true,
                targetConcept = SingleLanguageDependency,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/5381564949800872334",
            )
        }

        object Module : SimpleConcept(
            conceptName = "Module",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept, jetbrains_mps_lang_core.INamedConcept),
        ) {
            init { addConcept(this) }

            val id = SimpleProperty(
                "id",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/4225291329823310560",
            ).also(this::addProperty)

            val moduleVersion = SimpleProperty(
                "moduleVersion",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242370",
            ).also(this::addProperty)

            val compileInMPS = SimpleProperty(
                "compileInMPS",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242373",
            ).also(this::addProperty)

            val models = SimpleChildLink(
                simpleName = "models",
                isMultiple = true,
                isOptional = true,
                targetConcept = Model,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/474657388638618898",
            )
            val facets = SimpleChildLink(
                simpleName = "facets",
                isMultiple = true,
                isOptional = true,
                targetConcept = ModuleFacet,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242412",
            )
            val dependencies = SimpleChildLink(
                simpleName = "dependencies",
                isMultiple = true,
                isOptional = true,
                targetConcept = ModuleDependency,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242425",
            )
            val languageDependencies = SimpleChildLink(
                simpleName = "languageDependencies",
                isMultiple = true,
                isOptional = true,
                targetConcept = LanguageDependency,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242439",
            )
        }

        object Solution : SimpleConcept(
            conceptName = "Solution",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/7341098702109598211",
            directSuperConcepts = listOf(Module),
        ) {
            init { addConcept(this) }
        }

        object Language : SimpleConcept(
            conceptName = "Language",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/7341098702109598212",
            directSuperConcepts = listOf(Module),
        ) {
            init { addConcept(this) }
        }

        object DevKit : SimpleConcept(
            conceptName = "DevKit",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/7341098702109598213",
            directSuperConcepts = listOf(Module),
        ) {
            init { addConcept(this) }
        }

        object Repository : SimpleConcept(
            conceptName = "Repository",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept),
        ) {
            init { addConcept(this) }

            val modules = SimpleChildLink(
                simpleName = "modules",
                isMultiple = true,
                isOptional = true,
                targetConcept = Module,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/474657388638618903",
            )

            val projects = SimpleChildLink(
                simpleName = "projects",
                isMultiple = true,
                isOptional = true,
                targetConcept = Project,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/7064605579395546636",
            )

            val tempModules = SimpleChildLink(
                simpleName = "tempModules",
                isMultiple = true,
                isOptional = true,
                targetConcept = Module,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/8226136427470548682",
            )
        }

        object Project : SimpleConcept(
            conceptName = "Project",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept, jetbrains_mps_lang_core.INamedConcept),
        ) {
            init { addConcept(this) }

            val modules = SimpleChildLink(
                simpleName = "modules",
                isMultiple = true,
                isOptional = true,
                targetConcept = Module,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/4008363636171860450",
            )

            val projectModules = SimpleChildLink(
                simpleName = "projectModules",
                isMultiple = true,
                isOptional = true,
                targetConcept = ProjectModule,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/4201834143491306088",
            )
        }

        object ProjectModule : SimpleConcept(
            conceptName = "ProjectModule",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/4201834143491306084",
            directSuperConcepts = listOf(ModuleReference),
        ) {
            init { addConcept(this) }

            val virtualFolder = SimpleProperty(
                "virtualFolder",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4201834143491306084/4201834143491306085",
            ).also(this::addProperty)
        }

        object ModuleReference : SimpleConcept(
            conceptName = "ModuleReference",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/5782622473578468308",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept),
        ) {

            init { addConcept(this) }

            val module = SimpleReferenceLink(
                simpleName = "module",
                isOptional = false,
                targetConcept = Module,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/5782622473578468308/5782622473578468333",
            )
        }

        object ModelReference : SimpleConcept(
            conceptName = "ModelReference",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/6402965165736932003",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept),
        ) {
            init { addConcept(this) }

            val model = SimpleReferenceLink(
                simpleName = "model",
                isOptional = false,
                targetConcept = Model,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/6402965165736932003/6402965165736932004",
            )
        }

        object LanguageDependency : SimpleConcept(
            conceptName = "LanguageDependency",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/8958347146611575311",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept),
        ) {
            init { addConcept(this) }

            val uuid = SimpleProperty(
                "uuid",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/8958347146611575311/8958347146611575314",
            ).also(this::addProperty)

            val name = SimpleProperty(
                "name",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/8958347146611575311/8958347146611575315",
            ).also(this::addProperty)
        }

        object SingleLanguageDependency : SimpleConcept(
            conceptName = "SingleLanguageDependency",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242429",
            directSuperConcepts = listOf(LanguageDependency),
        ) {
            init { addConcept(this) }

            val version = SimpleProperty(
                "version",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242429/2206727074858242435",
            ).also(this::addProperty)
        }

        object DevkitDependency : SimpleConcept(
            conceptName = "DevkitDependency",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/8958347146611575318",
            directSuperConcepts = listOf(LanguageDependency),
        ) {
            init { addConcept(this) }
        }

        object ModuleFacet : SimpleConcept(
            conceptName = "ModuleFacet",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242403",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept),
        ) {
            init { addConcept(this) }
        }

        object JavaModuleFacet : SimpleConcept(
            conceptName = "JavaModuleFacet",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242406",
            directSuperConcepts = listOf(ModuleFacet),
        ) {
            init { addConcept(this) }

            val generated = SimpleProperty(
                "generated",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242406/2206727074858242407",
            ).also(this::addProperty)

            val path = SimpleProperty(
                "path",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242406/2206727074858242409",
            ).also(this::addProperty)
        }

        object ModuleDependency : SimpleConcept(
            conceptName = "ModuleDependency",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept),
        ) {
            init { addConcept(this) }

            val reexport = SimpleProperty(
                "reexport",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858242416",
            ).also(this::addProperty)

            val uuid = SimpleProperty(
                "uuid",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858242418",
            ).also(this::addProperty)

            val name = SimpleProperty(
                "name",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858242421",
            ).also(this::addProperty)

            val explicit = SimpleProperty(
                "explicit",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858750565",
            ).also(this::addProperty)

            val version = SimpleProperty(
                "version",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/2206727074858750570",
            ).also(this::addProperty)

            val scope = SimpleProperty(
                "scope",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/2206727074858242415/8438396892798826745",
            ).also(this::addProperty)
        }
    }

    object ModelixRuntimelang :
        SimpleLanguage("org.modelix.model.runtimelang", uid = "mps:b6980ebd-f01d-459d-a952-38740f6313b4") {

        override var includedConcepts = arrayOf(ModelServerInfo, RepositoryInfo, BranchInfo)

        object ModelServerInfo : SimpleConcept(
            conceptName = "ModelServerInfo",
            uid = "mps:b6980ebd-f01d-459d-a952-38740f6313b4/7113393488488348863",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept),
        ) {
            init { addConcept(this) }

            val repositories = SimpleChildLink(
                simpleName = "repositories",
                isMultiple = true,
                isOptional = true,
                targetConcept = RepositoryInfo,
                uid = "b6980ebd-f01d-459d-a952-38740f6313b4/7113393488488348863/7113393488488348866",
            )
        }

        object RepositoryInfo : SimpleConcept(
            conceptName = "RepositoryInfo",
            uid = "mps:b6980ebd-f01d-459d-a952-38740f6313b4/7113393488488348864",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept),
        ) {
            init { addConcept(this) }

            val id = SimpleProperty(
                "id",
                uid = "b6980ebd-f01d-459d-a952-38740f6313b4/7113393488488348864/7113393488488348870",
            ).also(this::addProperty)

            val branches = SimpleChildLink(
                simpleName = "branches",
                isMultiple = true,
                isOptional = true,
                targetConcept = BranchInfo,
                uid = "b6980ebd-f01d-459d-a952-38740f6313b4/7113393488488348864/7113393488488348868",
            )
        }

        object BranchInfo : SimpleConcept(
            conceptName = "BranchInfo",
            uid = "mps:b6980ebd-f01d-459d-a952-38740f6313b4/7113393488488348865",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept, jetbrains_mps_lang_core.INamedConcept),
        ) {
            init { addConcept(this) }
        }
    }

    fun getAllLanguages() = listOf(
        jetbrains_mps_lang_core,
        MPSRepositoryConcepts,
        ModelixRuntimelang,
    )
}

private fun SimpleConcept.childLink(uid: String) = object {
    private lateinit var name: String
    private lateinit var owner: SimpleConcept
    private var multiple: Boolean = true
    private var optional: Boolean = true
    private lateinit var targetConcept: () -> IConcept
    private val instance: IChildLink by lazy {
        SimpleChildLink(
            simpleName = name,
            uid = uid,
            isMultiple = multiple,
            isOptional = optional,
            targetConcept = targetConcept(),
        ).also { owner.addChildLink(it) }
    }

    operator fun getValue(ownerConcept: SimpleConcept, kotlinProperty: KProperty<*>): IChildLink {
        this.owner = ownerConcept
        this.name = kotlinProperty.name
        return instance
    }

    fun mandatory() = this.also { this.optional = false }
    fun optional() = this.also { this.optional = true }
    fun single() = this.also { this.multiple = false }
    fun multiple() = this.also { this.multiple = true }
    fun type(targetConcept: () -> IConcept) = also { this.targetConcept = targetConcept }
}
