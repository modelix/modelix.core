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

import jetbrains.mps.smodel.adapter.structure.link.SContainmentLinkAdapter
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept

data class MPSChildLink(val link: SContainmentLinkAdapter) : IChildLink {
    constructor(link: SContainmentLink) : this(link as SContainmentLinkAdapter)
    override val isMultiple: Boolean
        get() = link.isMultiple

    @Deprecated("use .targetConcept", ReplaceWith("targetConcept"))
    override val childConcept: IConcept
        get() = targetConcept
    override val targetConcept: IConcept
        get() = MPSConcept(link.targetConcept)

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

    override fun toReference(): IChildLinkReference = IChildLinkReference.fromIdAndName(getUID(), getSimpleName())
}
