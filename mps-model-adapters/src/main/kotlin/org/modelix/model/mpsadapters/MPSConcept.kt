/*
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

import jetbrains.mps.smodel.adapter.ids.SConceptId
import jetbrains.mps.smodel.adapter.structure.concept.SAbstractConceptAdapter
import jetbrains.mps.smodel.adapter.structure.concept.SConceptAdapterById
import jetbrains.mps.smodel.adapter.structure.concept.SInterfaceConceptAdapterById
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SInterfaceConcept
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.ILanguage
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink

data class MPSConcept(val concept: SAbstractConceptAdapter) : IConcept {
    constructor(concept: SAbstractConcept) : this(concept as SAbstractConceptAdapter)
    override fun getReference(): ConceptReference {
        return ConceptReference(getUID())
    }

    override val language: ILanguage
        get() = MPSLanguage(concept.language)

    override fun getUID(): String {
        val id: SConceptId = when (concept) {
            is SConceptAdapterById -> concept.id
            is SInterfaceConceptAdapterById -> concept.id
            else -> throw RuntimeException("Unknown concept type: $concept")
        }
        return "mps:" + id.serialize()
    }

    override fun getShortName(): String {
        return concept.name
    }

    override fun getLongName(): String {
        return concept.language.qualifiedName + "." + concept.name
    }

    override fun isAbstract(): Boolean {
        return concept.isAbstract
    }

    override fun isSubConceptOf(superConcept: IConcept?): Boolean {
        val mpsSuperConcept = superConcept as? MPSConcept ?: return false
        return concept.isSubConceptOf(mpsSuperConcept.concept)
    }

    override fun getDirectSuperConcepts(): List<IConcept> {
        return when (concept) {
            is SConcept -> listOfNotNull<SAbstractConcept>(ConceptWorkaround(concept).superConcept) +
                ConceptWorkaround(concept).superInterfaces
            is SInterfaceConcept -> ConceptWorkaround(concept).superInterfaces
            else -> emptyList<SAbstractConcept>()
        }.map { MPSConcept(it) }
    }

    override fun isExactly(concept: IConcept?): Boolean {
        val otherMpsConcept = concept as? MPSConcept ?: return false
        return this.concept == otherMpsConcept.concept
    }

    override fun getOwnProperties(): List<IProperty> {
        return concept.properties.filter { it.owner == concept }.map { MPSProperty(it) }
    }

    override fun getOwnChildLinks(): List<IChildLink> {
        return concept.containmentLinks.filter { it.owner == concept }.map { MPSChildLink(it) }
    }

    override fun getOwnReferenceLinks(): List<IReferenceLink> {
        return concept.referenceLinks.filter { it.owner == concept }.map { MPSReferenceLink(it) }
    }

    override fun getAllProperties(): List<IProperty> {
        return concept.properties.map { MPSProperty(it) }
    }

    override fun getAllChildLinks(): List<IChildLink> {
        return concept.containmentLinks.map { MPSChildLink(it) }
    }

    override fun getAllReferenceLinks(): List<IReferenceLink> {
        return concept.referenceLinks.map { MPSReferenceLink(it) }
    }

    override fun getProperty(name: String): IProperty {
        return MPSProperty(concept.properties.first { it.name == name })
    }

    override fun getChildLink(name: String): IChildLink {
        return MPSChildLink(concept.containmentLinks.first { it.name == name })
    }

    override fun getReferenceLink(name: String): IReferenceLink {
        return MPSReferenceLink(concept.referenceLinks.first { it.name == name })
    }
}
