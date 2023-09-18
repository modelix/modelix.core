/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.api

import kotlin.reflect.KProperty

object BuiltinLanguages {
    @Suppress("ClassName")
    object jetbrains_mps_lang_core : SimpleLanguage(name = "jetbrains.mps.lang.core", uid = "ceab5195-25ea-4f22-9b92-103b95ca8c0c") {
        object BaseConcept : SimpleConcept(conceptName = "BaseConcept", is_abstract = true, uid = "mps:ceab5195-25ea-4f22-9b92-103b95ca8c0c/1133920641626") {
            init { addConcept(this) }
            val virtualPackage by property("ceab5195-25ea-4f22-9b92-103b95ca8c0c/1133920641626/1193676396447")
            val smodelAttribute by childLink("ceab5195-25ea-4f22-9b92-103b95ca8c0c/1133920641626/5169995583184591170").multiple().optional().type { Attribute }
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
            val name by property("ceab5195-25ea-4f22-9b92-103b95ca8c0c/1169194658468/1169194664001")
        }
    }

    /**
     * These concepts are originally defined in
     * https://github.com/JetBrains/MPS-extensions/blob/5d96c3e69192f8902cf9aa7d846d05ccfb65253d/code/model-api/org.modelix.model.repositoryconcepts/models/org.modelix.model.repositoryconcepts.structure.mps ,
     * but to get rid of that dependency, they are redefined here, with their original IDs to stay compatible.
     */
    object MPSRepositoryConcepts : SimpleLanguage("org.modelix.model.repositoryconcepts", uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80") {

        object Model : SimpleConcept(
            conceptName = "Model",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept, jetbrains_mps_lang_core.INamedConcept),
        ) {
            init { addConcept(this) }
            val rootNodes = SimpleChildLink(
                simpleName = "rootNodes",
                isMultiple = true,
                isOptional = true,
                targetConcept = jetbrains_mps_lang_core.BaseConcept,
                uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618892/474657388638618900",
            )
        }

        object Module : SimpleConcept(
            conceptName = "Module",
            uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee80/474657388638618895",
            directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept, jetbrains_mps_lang_core.INamedConcept),
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
        }
    }

    fun getAllLanguages() = listOf(
        jetbrains_mps_lang_core,
        MPSRepositoryConcepts,
    )
}

private fun SimpleConcept.property(uid: String) = object {
    private lateinit var name: String
    private lateinit var owner: SimpleConcept
    private val instance: IProperty by lazy {
        SimpleProperty(name, uid = uid).also { owner.addProperty(it) }
    }
    operator fun getValue(ownerConcept: SimpleConcept, kotlinProperty: KProperty<*>): IProperty {
        this.owner = ownerConcept
        this.name = kotlinProperty.name
        return instance
    }
}

private fun SimpleConcept.childLink(uid: String) = object {
    private lateinit var name: String
    private lateinit var owner: SimpleConcept
    private var multiple: Boolean = true
    private var optional: Boolean = true
    private lateinit var targetConcept: () -> IConcept
    private val instance: IChildLink by lazy {
        SimpleChildLink(simpleName = name, uid = uid, isMultiple = multiple, isOptional = optional, targetConcept = targetConcept()).also { owner.addChildLink(it) }
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
