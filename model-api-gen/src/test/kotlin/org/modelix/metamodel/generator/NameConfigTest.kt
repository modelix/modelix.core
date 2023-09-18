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
package org.modelix.metamodel.generator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NameConfigTest {

    @Test
    fun `Language Name - simple`() {
        val input = "MyLanguage"
        val nameConfig = NameConfig().apply {
            languageClass.prefix = "PRE"
            languageClass.suffix = "SUF"
        }
        assertEquals("PREMyLanguageSUF", nameConfig.languageClass(input))
    }

    @Test
    fun `Language Name - qualified`() {
        val input = "org.example.MyLanguage"
        val nameConfig = NameConfig().apply {
            languageClass.prefix = "PRE"
            languageClass.suffix = "SUF"
        }
        assertEquals("PREorg_example_MyLanguageSUF", nameConfig.languageClass(input))
    }

    @Test
    fun `Concept type alias`() {
        val input = "MyConcept"
        val nameConfig = NameConfig().apply {
            conceptTypeAlias.prefix = "PRE"
            conceptTypeAlias.suffix = "SUF"
        }
        assertEquals("PREMyConceptSUF", nameConfig.conceptTypeAlias(input))
    }

    @Test
    fun `ConfigurableName - simple`() {
        val input = "MyInterface"
        val nameConfig = ConfigurableName().apply {
            prefix = "PRE"
            suffix = "SUF"
        }
        assertEquals("PREMyInterfaceSUF", nameConfig(input))
    }

    @Test
    fun `ConfigurableName - qualified`() {
        val input = "org.example.MyInterface"
        val nameConfig = ConfigurableName().apply {
            prefix = "PRE"
            suffix = "SUF"
        }
        assertFailsWith<IllegalArgumentException> { nameConfig(input) }
    }
}
