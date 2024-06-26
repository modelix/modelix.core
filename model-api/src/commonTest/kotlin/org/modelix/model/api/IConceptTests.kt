/*
 * Copyright (c) 2024.
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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IConceptTests {

    @Test
    fun isModuleSubConceptOfModuleBasedOnUid() {
        val moduleConcept = BuiltinLanguages.MPSRepositoryConcepts.Module
        val isSubConcept = moduleConcept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.Module.getReference())
        assertTrue(isSubConcept)
    }

    @Test
    fun isModelNotSubConceptOfModuleBasedOnUid() {
        val modelConcept = BuiltinLanguages.MPSRepositoryConcepts.Model
        val moduleConcept = BuiltinLanguages.MPSRepositoryConcepts.Module.getReference()
        val isNotSubConcept = modelConcept.isSubConceptOf(moduleConcept)
        assertFalse(isNotSubConcept)
    }

    @Test
    fun isModuleSubConceptOfNamedConceptBasedOnUid() {
        val moduleConcept = BuiltinLanguages.MPSRepositoryConcepts.Module
        val namedConcept = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.getReference()
        val isSubConcept = moduleConcept.isSubConceptOf(namedConcept)
        assertTrue(isSubConcept)
    }
}
