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

    fun `test MPSLanguageRepository cannot resolve the Module concept`() {
        val conceptUID = BuiltinLanguages.MPSRepositoryConcepts.Module.getReference().getUID()
        val moduleConcept = MPSLanguageRepository(mpsProject.repository).resolveConcept(conceptUID)

        // After making the repository language part of this plugin, this assertion will fail and this test can just be removed.
        assertNull(moduleConcept)
    }
}
