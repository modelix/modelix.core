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

import jetbrains.mps.smodel.adapter.structure.ref.SReferenceLinkAdapter
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IReferenceLink

data class MPSReferenceLink(val link: SReferenceLinkAdapter) : IReferenceLink {
    constructor(link: SReferenceLink) : this(link as SReferenceLinkAdapter)
    override fun getConcept(): IConcept {
        return MPSConcept(link.owner)
    }

    override fun getUID(): String {
        return link.id.serialize()
    }

    override fun getSimpleName(): String {
        return link.name
    }

    override val isOptional: Boolean
        get() = link.isOptional

    override val targetConcept: IConcept
        get() = MPSConcept(link.targetConcept)
}