package org.modelix.model.mpsadapters

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.ILanguageRepository

class ConceptResolutionTest : MpsAdaptersTestBase("SimpleProject") {

    fun `test resolution of Module concept`() {
        val conceptUID = BuiltinLanguages.MPSRepositoryConcepts.Module.getReference().getUID()
        val moduleConcept = ILanguageRepository.resolveConcept(ConceptReference(conceptUID))

        // There was an issue where MPSLanguageRepository resolved a concept even though it wasn't loaded and no
        // metamodel information was available. If a concept can be resolved then it should provide more information
        // than just the ID that it extracted from the concept reference.
        assertEquals("Module", moduleConcept.getShortName())
        assertContainsElements(moduleConcept.getOwnChildLinks().map { it.getSimpleName() }, "models")
    }
}
