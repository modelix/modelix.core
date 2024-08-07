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

import jetbrains.mps.smodel.adapter.structure.property.SPropertyAdapter
import org.jetbrains.mps.openapi.language.SProperty
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IProperty
import org.modelix.model.api.IPropertyReference

data class MPSProperty(val property: SPropertyAdapter) : IProperty {
    constructor(property: SProperty) : this(property as SPropertyAdapter)
    override fun getConcept(): IConcept {
        return MPSConcept(property.owner)
    }

    override fun getUID(): String {
        return property.id.serialize()
    }

    override fun getSimpleName(): String {
        return property.name
    }

    override val isOptional: Boolean
        get() = true

    override fun toReference(): IPropertyReference = IPropertyReference.fromIdAndName(getUID(), getSimpleName())
}
