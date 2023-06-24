/*
 * Copyright 2003-2023 JetBrains s.r.o.
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

import jetbrains.mps.smodel.adapter.structure.concept.SAbstractConceptAdapter
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ILanguage
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink

data class MPSConcept(val concept: SAbstractConceptAdapter): IConcept {
    constructor(concept: SAbstractConcept) : this(concept as SAbstractConceptAdapter)
    override fun getReference(): IConceptReference {
        TODO("Not yet implemented")
    }

    override val language: ILanguage?
        get() = TODO("Not yet implemented")

    override fun getUID(): String {
        TODO("Not yet implemented")
    }

    override fun getShortName(): String {
        TODO("Not yet implemented")
    }

    override fun getLongName(): String {
        TODO("Not yet implemented")
    }

    override fun isAbstract(): Boolean {
        TODO("Not yet implemented")
    }

    override fun isSubConceptOf(superConcept: IConcept?): Boolean {
        TODO("Not yet implemented")
    }

    override fun getDirectSuperConcepts(): List<IConcept> {
        TODO("Not yet implemented")
    }

    override fun isExactly(concept: IConcept?): Boolean {
        TODO("Not yet implemented")
    }

    override fun getOwnProperties(): List<IProperty> {
        TODO("Not yet implemented")
    }

    override fun getOwnChildLinks(): List<IChildLink> {
        TODO("Not yet implemented")
    }

    override fun getOwnReferenceLinks(): List<IReferenceLink> {
        TODO("Not yet implemented")
    }

    override fun getAllProperties(): List<IProperty> {
        TODO("Not yet implemented")
    }

    override fun getAllChildLinks(): List<IChildLink> {
        TODO("Not yet implemented")
    }

    override fun getAllReferenceLinks(): List<IReferenceLink> {
        TODO("Not yet implemented")
    }

    override fun getProperty(name: String): IProperty {
        TODO("Not yet implemented")
    }

    override fun getChildLink(name: String): IChildLink {
        TODO("Not yet implemented")
    }

    override fun getReferenceLink(name: String): IReferenceLink {
        TODO("Not yet implemented")
    }
}