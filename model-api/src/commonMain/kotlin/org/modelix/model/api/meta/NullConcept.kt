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

import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ILanguage
import org.modelix.model.area.IArea

object NullConcept : EmptyConcept(), IConceptReference {
    override fun getReference(): IConceptReference = this

    override val language: ILanguage?
        get() = null

    override fun getUID(): String = "null"

    override fun getShortName(): String = "null"

    override fun getLongName(): String = getShortName()

    override fun resolve(area: IArea?): IConcept? {
        return this
    }

    override fun serialize(): String = "null"
}