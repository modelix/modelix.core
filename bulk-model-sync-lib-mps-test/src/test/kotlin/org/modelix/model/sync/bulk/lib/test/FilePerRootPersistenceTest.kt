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

package org.modelix.model.sync.bulk.lib.test

import jetbrains.mps.smodel.SNodeUtil
import jetbrains.mps.smodel.adapter.ids.MetaIdHelper
import jetbrains.mps.smodel.adapter.ids.SConceptId
import jetbrains.mps.smodel.adapter.structure.concept.SConceptAdapterById
import jetbrains.mps.smodel.language.ConceptRegistry
import jetbrains.mps.smodel.language.StructureRegistry
import jetbrains.mps.smodel.runtime.ConceptDescriptor
import jetbrains.mps.smodel.runtime.illegal.IllegalConceptDescriptor
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.module.SRepository

class FilePerRootPersistenceTest : MPSTestBase() {

    fun `test node name is used as file name (testdata filePerRootPersistence)`() = runWrite {
        val projectModules = mpsProject.projectModules.toList()
        // The language isn't part of the project on purpose. Missing metamodel information causes MPS to use node IDs
        // instead of names for the file name.
        assertEquals(1, projectModules.size)

        // Ensure the language is also not loaded via a project/global library
        assertEquals(0, mpsProject.repository.modules.filter { it.moduleName == "languageA" }.size)

        val solutionA = projectModules.single { it.moduleName == "solutionA" }
        val modelA = solutionA.models.single { it.name.longName == "solutionA.modelA1" }
        val rootNodes = modelA.rootNodes.toList()
        assertEquals(2, rootNodes.size)

        // SNode.getName() is used for the file name, but if the language is missing then getName() returns null even
        // though reading the name property directly returns the correct value. That's because getName() checks if the
        // node is an instance of INamedConcept, which is unnecessary.
        // Here we verify that this bug still exists in MPS. If these assertions fail we can remove the workaround.
        assertEquals(null, rootNodes[0].name)
        assertEquals(null, rootNodes[1].name)
        assertEquals("MyRootNode1", rootNodes[0].getProperty(SNodeUtil.property_INamedConcept_name))
        assertEquals("MyRootNode2", rootNodes[1].getProperty(SNodeUtil.property_INamedConcept_name))

        enableWorkaroundForFilePerRootPersistence(mpsProject.repository)

        // After applying the workaround getName() should return the correct value
        assertEquals("MyRootNode1", rootNodes[0].name)
        assertEquals("MyRootNode2", rootNodes[1].name)
    }

    private fun enableWorkaroundForFilePerRootPersistence(repository: SRepository) {
        val structureRegistry: StructureRegistry = ConceptRegistry.getInstance().readField("myStructureRegistry")
        val myConceptDescriptorsById: MutableMap<SConceptId, ConceptDescriptor> = structureRegistry.readField("myConceptDescriptorsById")

        repository.modelAccess.runWriteAction {
            repository.modules
                .asSequence()
                .flatMap { it.models }
                .mapNotNull { it as? EditableSModel }
                .filter { it.isChanged }
                .flatMap { it.rootNodes }
                .mapNotNull { (it.concept as? SConceptAdapterById) }
                .forEach {
                    myConceptDescriptorsById.putIfAbsent(it.id, DummyNamedConceptDescriptor(it))
                }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> Any.readField(name: String): R {
        return this::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(this) as R
    }

    private class DummyNamedConceptDescriptor(concept: SConceptAdapterById) : ConceptDescriptor by IllegalConceptDescriptor(concept.id) {
        override fun isAssignableTo(other: SConceptId?): Boolean {
            return MetaIdHelper.getConcept(SNodeUtil.concept_INamedConcept) == other
        }

        override fun getSuperConceptId(): SConceptId {
            return MetaIdHelper.getConcept(SNodeUtil.concept_BaseConcept)
        }

        override fun getAncestorsIds(): MutableSet<SConceptId> {
            return mutableSetOf(MetaIdHelper.getConcept(SNodeUtil.concept_INamedConcept))
        }

        override fun getParentsIds(): MutableList<SConceptId> {
            return mutableListOf(MetaIdHelper.getConcept(SNodeUtil.concept_INamedConcept))
        }
    }
}
