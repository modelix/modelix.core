package org.modelix.model.api

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

            val smodelAttribute = SimpleChildLink(
                simpleName = "smodelAttribute",
                isMultiple = true,
                isOptional = true,
                targetConcept = Attribute,
                uid = "ceab5195-25ea-4f22-9b92-103b95ca8c0c/1133920641626/5169995583184591170",
            ).also(this::addChildLink)
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

        object INamedConcept : SimpleConcept(
            conceptName = "INamedConcept",
            is_abstract = true,
            uid = "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/1169194658468",
        ) {
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
                isOrdered = false,
                targetConcept = jetbrains_mps_lang_core.BaseConcept,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/474657388638618900",
            ).also(this::addChildLink)

            val modelImports = SimpleChildLink(
                simpleName = "modelImports",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = ModelReference,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/6402965165736931000",
            ).also(this::addChildLink)

            val usedLanguages = SimpleChildLink(
                simpleName = "usedLanguages",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = SingleLanguageDependency,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/5381564949800872334",
            ).also(this::addChildLink)
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

            val readonlyStubModule = SimpleProperty(
                "readonlyStubModule",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/4225291355523310000",
            ).also(this::addProperty)

            val models = SimpleChildLink(
                simpleName = "models",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = Model,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/474657388638618898",
            ).also(this::addChildLink)

            val facets = SimpleChildLink(
                simpleName = "facets",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = ModuleFacet,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242412",
            ).also(this::addChildLink)

            val dependencies = SimpleChildLink(
                simpleName = "dependencies",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = ModuleDependency,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242425",
            ).also(this::addChildLink)

            val languageDependencies = SimpleChildLink(
                simpleName = "languageDependencies",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = LanguageDependency,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/2206727074858242439",
            ).also(this::addChildLink)
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

            val generators = SimpleChildLink(
                simpleName = "generators",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = Generator,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/7018594982789597990",
            ).also(this::addChildLink)

            val extendedLanguages = SimpleChildLink(
                simpleName = "extendedLanguages",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = ModuleReference,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/7440567989974771396",
            ).also(this::addChildLink)
        }

        object DevKit : SimpleConcept(
            conceptName = "DevKit",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/7341098702109598213",
            directSuperConcepts = listOf(Module),
        ) {
            init { addConcept(this) }

            val exportedLanguages = SimpleChildLink(
                simpleName = "exportedLanguages",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = ModuleReference,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/3386077560723097553",
            ).also(this::addChildLink)

            val exportedSolutions = SimpleChildLink(
                simpleName = "exportedSolutions",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = ModuleReference,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/3386077560723097554",
            ).also(this::addChildLink)

            val extendedDevkits = SimpleChildLink(
                simpleName = "extendedDevkits",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = ModuleReference,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/3386077560723097555",
            ).also(this::addChildLink)
        }

        object Generator : SimpleConcept(
            conceptName = "Generator",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/7018594982789597991",
            directSuperConcepts = listOf(Module),
        ) {
            init { addConcept(this) }

            val alias = SimpleProperty(
                "alias",
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895/5552089503111831268",
            ).also(this::addProperty)
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
                isOrdered = false,
                targetConcept = Module,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/474657388638618903",
            ).also(this::addChildLink)

            val projects = SimpleChildLink(
                simpleName = "projects",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = Project,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/7064605579395546636",
            ).also(this::addChildLink)

            val tempModules = SimpleChildLink(
                simpleName = "tempModules",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = Module,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618902/8226136427470548682",
            ).also(this::addChildLink)
        }

        object Project : SimpleConcept(
            conceptName = "Project",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept, jetbrains_mps_lang_core.INamedConcept),
        ) {
            init { addConcept(this) }

            @Deprecated("Use Repository.modules")
            val modules = SimpleChildLink(
                simpleName = "modules",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = Module,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/4008363636171860450",
            ).also(this::addChildLink)

            val projectModules = SimpleChildLink(
                simpleName = "projectModules",
                isMultiple = true,
                isOptional = true,
                isOrdered = false,
                targetConcept = ProjectModule,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/4008363636171860313/4201834143491306088",
            ).also(this::addChildLink)
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
            ).also(this::addChildLink)
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
            ).also(this::addChildLink)
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

fun IReadableNode.getName() = getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference())
fun IReadableNode.getStereotype() = getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.stereotype.toReference())
