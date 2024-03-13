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
package org.modelix.model.metameta

import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ILanguage
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.area.IArea

data class PersistedConcept(val id: Long, val uid: String?) : IConcept, IConceptReference {
    override val language: ILanguage?
        get() = throw UnsupportedOperationException()

    override fun getChildLink(name: String): IChildLink {
        throw UnsupportedOperationException()
    }

    override fun getDirectSuperConcepts(): List<IConcept> {
        throw UnsupportedOperationException()
    }

    override fun getLongName(): String {
        throw UnsupportedOperationException()
    }

    override fun getProperty(name: String): IProperty {
        throw UnsupportedOperationException()
    }

    override fun getReference(): IConceptReference {
        // The reference uses the ID and not the UID because of
        // how MetaModelSynchronizer, MetaModelBranch and CLTree work together.
        //
        // `MetaModelSynchronizer#storeConcept` creates a `PersistedConcept`.
        // `PersistedConcept#uid` is the UID of the original concept.
        // `PersistedConcept#id` is the node ID for the node that holds metadata for the original concept.
        //
        // CLTree#createNewNodes uses the value `IConceptReference#getUID(): String` for `CPNode.concept`.
        //
        // `MetaModelBranch#resolveConcept` checks if `IConceptReference#getUID()` is hexadecimal value.
        // If that is the case, it tries to resolve the node with the original concepts metadata
        // and read UID of the original concept to find the original concept by UID.
        // The `MetaModelSynchronizer#storeConcept` stored the node with metadata with the ID `this.id`.
        //
        // So for `MetaModelBranch#resolveConcept` to attempt the resolution,
        // we need `this.getReference().getUID()`
        // to return the hexadecimal value `idAsHexString` and not the actual `this.uid`.
        val idAsHexString = id.toString(16)
        return ConceptReference(idAsHexString)
    }

    override fun getReferenceLink(name: String): IReferenceLink {
        throw UnsupportedOperationException()
    }

    override fun getShortName(): String {
        throw UnsupportedOperationException()
    }

    override fun getUID(): String {
        return uid!!
    }

    override fun isAbstract(): Boolean {
        throw UnsupportedOperationException()
    }

    override fun isExactly(concept: IConcept?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun isSubConceptOf(superConcept: IConcept?): Boolean {
        throw UnsupportedOperationException()
    }

    override fun resolve(area: IArea?): IConcept? {
        throw UnsupportedOperationException()
    }

    override fun serialize(): String {
        TODO("Not yet implemented")
    }

    override fun getAllChildLinks(): List<IChildLink> {
        throw UnsupportedOperationException()
    }

    override fun getAllProperties(): List<IProperty> {
        throw UnsupportedOperationException()
    }

    override fun getAllReferenceLinks(): List<IReferenceLink> {
        throw UnsupportedOperationException()
    }

    override fun getOwnChildLinks(): List<IChildLink> {
        throw UnsupportedOperationException()
    }

    override fun getOwnProperties(): List<IProperty> {
        throw UnsupportedOperationException()
    }

    override fun getOwnReferenceLinks(): List<IReferenceLink> {
        throw UnsupportedOperationException()
    }
}
