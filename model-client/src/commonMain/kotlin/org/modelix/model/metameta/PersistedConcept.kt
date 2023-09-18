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
package org.modelix.model.metameta

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
        throw UnsupportedOperationException()
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
