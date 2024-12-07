package org.modelix.model.api

import io.kotest.inspectors.forAll
import io.kotest.matchers.string.shouldStartWith
import kotlin.test.Test
import kotlin.test.assertEquals

class BuiltinLanguagesTest {

    @Test
    fun allPropertiesAreListedWithoutPriorAccess() {
        val properties = BuiltinLanguages.MPSRepositoryConcepts.Model.getAllProperties()

        // This trivial assertion is relevant
        // because previously, some properties were only after the property was directly accessed.
        // For example, Model.stereotype would be included in Model.getAllProperties() before
        // Model.stereotype was accessed once directly.
        assertEquals(4, properties.size)
    }

    @Test
    fun allChildrenAreListed() {
        val childLinks = BuiltinLanguages.MPSRepositoryConcepts.Model.getOwnChildLinks()

        // This trivial assertion is relevant
        // because previously, children were not listed at all in Model.getOwnChildLinks().
        // They were only accessible by directly calling Model.modelImports for example.
        assertEquals(3, childLinks.size)
    }

    @Test
    fun allBuiltInLanguagesHaveMpsConceptId() {
        val concepts = BuiltinLanguages.getAllLanguages()
            .flatMap { it.getConcepts() }

        concepts.forAll { concept: IConcept ->
            concept.getUID().shouldStartWith("mps:")
        }
    }
}
