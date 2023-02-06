package org.modelix.metamodel.generator

import org.junit.Test
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