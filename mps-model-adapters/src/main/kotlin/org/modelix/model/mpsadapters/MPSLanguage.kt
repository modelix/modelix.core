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
package org.modelix.model.mpsadapters

import jetbrains.mps.smodel.adapter.ids.MetaIdHelper
import org.jetbrains.mps.openapi.language.SLanguage
import org.modelix.model.api.IConcept
import org.modelix.model.api.ILanguage

data class MPSLanguage(val language: SLanguage) : ILanguage {
    override fun getUID(): String {
        return MetaIdHelper.getLanguage(language).serialize()
    }

    override fun getName(): String {
        return language.qualifiedName
    }

    override fun getConcepts(): List<IConcept> {
        return language.concepts.map { MPSConcept(it) }
    }
}
