package org.modelix.model.sync.bulk

import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleInclusionTest {

    private val allModules = setOf(
        "a", "b", "c",
        "prefix.a", "prefix.b", "prefix.c",
        "prefix2.a", "prefix2.b", "prefix2.c",
    )

    @Test
    @JsName("no_modules_are_included_by_default")
    fun `no modules are included by default`() {
        val result = allModules.filter { isModuleIncluded(it, emptySet(), emptySet()) }
        assertTrue { result.isEmpty() }
    }

    @Test
    @JsName("modules_are_included_correctly")
    fun `modules are included correctly`() {
        val result = allModules.filter {
            isModuleIncluded(
                moduleName = it,
                includedModules = setOf("a", "b"),
                includedPrefixes = setOf("prefix2"),
            )
        }.toSet()

        val expected = setOf("a", "b", "prefix2.a", "prefix2.b", "prefix2.c")
        assertEquals(expected, result)
    }

    @Test
    @JsName("modules_are_excluded_directly")
    fun `modules are excluded correctly`() {
        val result = allModules.filter {
            isModuleIncluded(
                moduleName = it,
                includedModules = setOf("a", "b"),
                includedPrefixes = setOf("prefix"),
                excludedModules = setOf("prefix.b"),
                excludedPrefixes = setOf("prefix2"),
            )
        }.toSet()

        val expected = setOf("a", "b", "prefix.a", "prefix.c")
        assertEquals(expected, result)
    }

    @Test
    @JsName("exclusion_has_priority_over_inclusion")
    fun `exclusion has priority over inclusion`() {
        val result = allModules.filter {
            isModuleIncluded(
                it,
                includedModules = setOf("a", "b"),
                includedPrefixes = setOf("prefix"),
                excludedModules = setOf("a", "b"),
                excludedPrefixes = setOf("prefix"),
            )
        }
        assertTrue { result.isEmpty() }
    }
}
