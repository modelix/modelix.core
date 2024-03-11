/*
 * Copyright (c) 2023.
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

package org.modelix.model.sync.bulk.gradle.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.xmlunit.builder.Input
import org.xmlunit.xpath.JAXPXPathEngine
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PullTest {

    private val solutionsBaseDir = File("build/test-repo/solutions")
    private val solution1Xml = Input.fromString(
        solutionsBaseDir
            .resolve("GraphSolution/models/GraphSolution.example.mps")
            .readText(),
    ).build()

    private val solution2Xml = Input.fromString(
        solutionsBaseDir
            .resolve("GraphSolution2/models/GraphSolution2.example.mps")
            .readText(),
    ).build()

    @Test
    fun `properties were synced to local`() {
        val properties = JAXPXPathEngine()
            .selectNodes("model/node/node[@concept='1DmExO']/property", solution1Xml)

        val actual = properties.map { it.attributes.getNamedItem("value").nodeValue }
        val expected = listOf("X", "Y", "Z", "NewNode", "D", "E")

        assertContentEquals(expected, actual)
    }

    @Test
    fun `added child was synced to local`() {
        val xpath = JAXPXPathEngine()
        val nodes = xpath.selectNodes("model/node/node[@concept='1DmExO']", solution1Xml)

        val nodeNames = nodes.flatMap { xpath.selectNodes("property", it) }
            .map { it.attributes.getNamedItem("value").nodeValue }

        assertEquals(6, nodes.count())
        assertContains(nodeNames, "NewNode")
    }

    @Test
    fun `references were synced to local`() {
        val references = JAXPXPathEngine()
            .selectNodes("model/node/node[@id='pSCM1J8Fg1']/ref", solution1Xml)

        val actual = references.map { it.attributes.getNamedItem("node").nodeValue }
        val expected = listOf("pSCM1J8FfX", "pSCM1J8FfZ")

        assertContentEquals(expected, actual)
    }

    @Test
    fun `existing cross-module references are intact`() {
        val reference = JAXPXPathEngine()
            .selectNodes("model/node[@id='27XSKLmUrj6']/ref", solution2Xml)

        val actual = reference.map { it.attributes.getNamedItem("to").nodeValue }
        val expected = listOf("c5sg:pSCM1J8y9y")

        assertEquals(expected, actual)
    }

    @Test
    fun `newly created cross-module references are synced`() {
        val reference = JAXPXPathEngine()
            .selectNodes("model/node[@id='pSCM1J8y9y']/ref", solution1Xml)

        val actual = reference.map { it.attributes.getNamedItem("to").nodeValue }
        val expected = listOf("gywm:27XSKLmUrj6")

        assertEquals(expected, actual)
    }
}
