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

package org.modelix.model.api.meta

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink

abstract class EmptyConcept : IConcept {
    override fun isAbstract(): Boolean = true

    override fun isSubConceptOf(superConcept: IConcept?): Boolean = superConcept == this

    override fun getDirectSuperConcepts(): List<IConcept> = emptyList()

    override fun isExactly(concept: IConcept?): Boolean = concept == this

    override fun getOwnProperties(): List<IProperty> = emptyList()

    override fun getOwnChildLinks(): List<IChildLink> = emptyList()

    override fun getOwnReferenceLinks(): List<IReferenceLink> = emptyList()

    override fun getAllProperties(): List<IProperty> = emptyList()

    override fun getAllChildLinks(): List<IChildLink> = emptyList()

    override fun getAllReferenceLinks(): List<IReferenceLink> = emptyList()

    override fun getProperty(name: String): IProperty {
        throw IllegalArgumentException("Cannot get property '$name'. No concept information available for '${getUID()}'.")
    }

    override fun getChildLink(name: String): IChildLink {
        throw IllegalArgumentException("Cannot get link '$name'. No concept information available for '${getUID()}'.")
    }

    override fun getReferenceLink(name: String): IReferenceLink {
        throw IllegalArgumentException("Cannot get link '$name'. No concept information available for '${getUID()}'.")
    }
}
