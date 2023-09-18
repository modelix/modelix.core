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

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.decodeFromString
import org.modelix.model.data.LanguageData
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains

class KotlinGeneratorTest {

    @Test
    fun test() {
        val input = """
            name: org.modelix.entities
            concepts:
            - name: Entity
              properties:
              - name: name
              children:
              - name: properties
                type: Property
                multiple: true
                optional: true
            - name: Property
              children:
              - name: type
                type: Type
                optional: false
            - name: Type
            - name: EntityType
              extends:
              - Type
              references:
              - name: entity
                type: Entity
                optional: false
            enums: []
        """.trimIndent()

        val language = Yaml.default.decodeFromString<LanguageData>(input)
        // val outputDir = File(".").toPath().resolve("build").resolve("test-generator-output")
        val outputDir = File("build/test-generator-output").toPath()
        MetaModelGenerator(outputDir).generate(LanguageSet(listOf(language)).process())
    }

    @Test
    fun test_ts() {
        val input = """
            name: org.modelix.entities
            concepts:
            - name: Entity
              properties:
              - name: name
              children:
              - name: properties
                type: Property
                multiple: true
                optional: true
            - name: Property
              children:
              - name: type
                type: Type
                optional: false
            - name: Type
            - name: EntityType
              extends:
              - Type
              references:
              - name: entity
                type: Entity
                optional: false
            enums: []
        """.trimIndent()

        val language = Yaml.default.decodeFromString<LanguageData>(input)
        // val outputDir = File(".").toPath().resolve("build").resolve("test-generator-output")
        val outputDir = File("build/test-generator-output").toPath()
        TypescriptMMGenerator(outputDir).generate(LanguageSet(listOf(language)).process())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `uses configured names`() {
        val input = """
            name: org.modelix.entities
            concepts:
            - name: Entity
              properties: []
              children: []
            enums: []
        """.trimIndent()

        val language = Yaml.default.decodeFromString<LanguageData>(input)
        val outputDir = createTempDirectory()
        try {
            val nameConfig = NameConfig().apply {
                conceptTypeAlias.prefix = "AAAA"
                languageClass.prefix = "BBBB"
                typedNode.prefix = "CCCC"
                typedNodeImpl.prefix = "DDDD"
                untypedConcept.prefix = "EEEE"
                typedConcept.prefix = "FFFF"
            }

            MetaModelGenerator(outputDir, nameConfig).generate(LanguageSet(listOf(language)).process())

            val fileContents = outputDir.resolve("org/modelix/entities/Entity.kt").readText()
            setOf(
                nameConfig.conceptTypeAlias.prefix,
                nameConfig.languageClass.prefix,
                nameConfig.typedNode.prefix,
                nameConfig.typedNodeImpl.prefix,
                nameConfig.untypedConcept.prefix,
                nameConfig.typedConcept.prefix,
            ).forEach { needle ->
                assertContains(fileContents, needle)
            }
        } finally {
            outputDir.deleteRecursively()
        }
    }
}
