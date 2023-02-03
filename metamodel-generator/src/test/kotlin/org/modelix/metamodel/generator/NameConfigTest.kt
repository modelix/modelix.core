package org.modelix.metamodel.generator

import org.junit.Test
import kotlin.test.assertEquals


class NameConfigTest {

    @Test
    fun `Language Name - simple`() {
        val input = "MyLanguage"
        val nameConfig = NameConfig(languagePrefix = "PRE", languageSuffix = "SUF")
        assertEquals("PREMyLanguageSUF", nameConfig.languageClassName(input))
    }

    @Test
    fun `Language Name - qualified`() {
        val input = "org.example.MyLanguage"
        val nameConfig = NameConfig(languagePrefix = "PRE", languageSuffix = "SUF")
        assertEquals("PREorg_example_MyLanguageSUF", nameConfig.languageClassName(input))
    }

    @Test
    fun `Node Wrapper Interface Name - simple`() {
        val input = "MyInterface"
        val nameConfig = NameConfig(nodeWrapperInterfacePrefix = "PRE", nodeWrapperInterfaceSuffix = "SUF")
        assertEquals("PREMyInterfaceSUF", nameConfig.nodeWrapperInterfaceName(input))
    }

    @Test
    fun `Node Wrapper Interface Name - qualified`() {
        val input = "org.example.MyInterface"
        val nameConfig = NameConfig(nodeWrapperInterfacePrefix = "PRE", nodeWrapperInterfaceSuffix = "SUF")
        assertEquals("org.example.PREMyInterfaceSUF", nameConfig.nodeWrapperInterfaceName(input))
    }

    @Test
    fun `Node Wrapper Impl Name - simple`() {
        val input = "MyImpl"
        val nameConfig = NameConfig(nodeWrapperImplPrefix = "PRE", nodeWrapperImplSuffix = "SUF")
        assertEquals("PREMyImplSUF", nameConfig.nodeWrapperImplName(input))
    }

    @Test
    fun `Node Wrapper Impl Name - qualified`() {
        val input = "org.example.MyImpl"
        val nameConfig = NameConfig(nodeWrapperImplPrefix = "PRE", nodeWrapperImplSuffix = "SUF")
        assertEquals("org.example.PREMyImplSUF", nameConfig.nodeWrapperImplName(input))
    }

    @Test
    fun `Concept Object Name - simple`() {
        val input = "MyConceptObject"
        val nameConfig = NameConfig(conceptObjectPrefix = "PRE", conceptObjectSuffix = "SUF")
        assertEquals("PREMyConceptObjectSUF", nameConfig.conceptObjectName(input))
    }

    @Test
    fun `Concept Object Name - qualified`() {
        val input = "org.example.MyConceptObject"
        val nameConfig = NameConfig(conceptObjectPrefix = "PRE", conceptObjectSuffix = "SUF")
        assertEquals("org.example.PREMyConceptObjectSUF", nameConfig.conceptObjectName(input))
    }

    @Test
    fun `Concept Wrapper Interface Name - simple`() {
        val input = "MyConceptInterface"
        val nameConfig = NameConfig(conceptWrapperInterfacePrefix = "PRE", conceptWrapperInterfaceSuffix = "SUF")
        assertEquals("PREMyConceptInterfaceSUF", nameConfig.conceptWrapperInterfaceName(input))
    }

    @Test
    fun `Concept Wrapper Interface Name - qualified`() {
        val input = "org.example.MyConceptInterface"
        val nameConfig = NameConfig(conceptWrapperInterfacePrefix = "PRE", conceptWrapperInterfaceSuffix = "SUF")
        assertEquals("org.example.PREMyConceptInterfaceSUF", nameConfig.conceptWrapperInterfaceName(input))
    }

    @Test
    fun `Concept Wrapper Impl Name - simple`() {
        val input = "MyConceptImpl"
        val nameConfig = NameConfig(conceptWrapperImplPrefix = "PRE", conceptWrapperImplSuffix = "SUF")
        assertEquals("PREMyConceptImplSUF", nameConfig.conceptWrapperImplName(input))
    }

    @Test
    fun `Concept Wrapper Impl Name - qualified`() {
        val input = "org.example.MyConceptImpl"
        val nameConfig = NameConfig(conceptWrapperImplPrefix = "PRE", conceptWrapperImplSuffix = "SUF")
        assertEquals("org.example.PREMyConceptImplSUF", nameConfig.conceptWrapperImplName(input))
    }
}