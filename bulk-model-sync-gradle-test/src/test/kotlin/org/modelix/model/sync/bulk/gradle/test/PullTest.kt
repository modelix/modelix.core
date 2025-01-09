package org.modelix.model.sync.bulk.gradle.test

import GraphLang.L_GraphLang
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.modelix.model.api.ConceptReference
import org.xmlunit.builder.Input
import org.xmlunit.xpath.JAXPXPathEngine
import java.io.File
import javax.xml.transform.Source
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

    private fun getRegisteredConcepts(xml: Source): Map<ConceptReference, String> {
        val conceptNodes = JAXPXPathEngine().selectNodes("model/registry/language/concept", xml)
        return conceptNodes.associate { c ->
            val conceptId = c.attributes.getNamedItem("id").nodeValue
            val languageId = c.parentNode.attributes.getNamedItem("id").nodeValue
            val shortId = c.attributes.getNamedItem("index").nodeValue
            ConceptReference("mps:$languageId/$conceptId") to shortId
        }
    }

    @Test
    fun `properties were synced to local`() {
        val shortConceptId = getRegisteredConcepts(solution1Xml)[L_GraphLang.Node.untyped().getReference()]
        val properties = JAXPXPathEngine()
            .selectNodes("model/node/node[@concept='$shortConceptId']/property", solution1Xml)

        val actual = properties.map { it.attributes.getNamedItem("value").nodeValue }
        val expected = listOf("X", "Y", "Z", "NewNode", "D", "E")

        assertContentEquals(expected, actual)
    }

    @Test
    fun `added child was synced to local`() {
        val shortConceptId = getRegisteredConcepts(solution1Xml)[L_GraphLang.Node.untyped().getReference()]
        val xpath = JAXPXPathEngine()
        val nodes = xpath.selectNodes("model/node/node[@concept='$shortConceptId']", solution1Xml)

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
