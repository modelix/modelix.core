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
