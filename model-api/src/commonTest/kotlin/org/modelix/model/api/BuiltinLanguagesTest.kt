/*
 * Copyright (c) 2023-2024.
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

package org.modelix.model.api

import kotlin.test.Test
import kotlin.test.assertEquals

class BuiltinLanguagesTest {

    @Test
    fun allPropertiesAreListedWithoutPriorAccess() {
        val properties = BuiltinLanguages.MPSRepositoryConcepts.Model.getAllProperties()

        // This trivial assertion is relevant
        // because previously, some properties were only after the property was directly accessed.
        // For example, Model.stereotype would be included in Model.getAllProperties() before
        // Model.stereotype was accessed once directly.
        assertEquals(4, properties.size)
    }

    @Test
    fun allChildrenAreListed() {
        val childLinks = BuiltinLanguages.MPSRepositoryConcepts.Model.getOwnChildLinks()

        // This trivial assertion is relevant
        // because previously, children were not listed at all in Model.getOwnChildLinks().
        // They were only accessible by directly calling Model.modelImports for example.
        assertEquals(3, childLinks.size)
    }
}
