package org.modelix.model.client2

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class ReplicatedModelParametersTest {

    @Test
    fun test_valid_branchId() {
        // Valid: branchId is positional 2nd arg
        ReplicatedModelParameters("repo", "branch", IdSchemeJS.MODELIX)
    }

    @Test
    fun test_valid_versionHash_with_readonly() {
        // Valid: versionHash with readonly=true (branchId must be null)
        ReplicatedModelParameters(
            repositoryId = "repo",
            branchId = null,
            idScheme = IdSchemeJS.MODELIX,
            readonly = true,
            versionHash = "hash",
        )
    }

    @Test
    fun test_invalid_both_branchId_and_versionHash() {
        // Invalid: cannot specify both branchId and versionHash
        assertFailsWith<IllegalArgumentException> {
            ReplicatedModelParameters(
                repositoryId = "repo",
                branchId = "branch",
                idScheme = IdSchemeJS.MODELIX,
                versionHash = "hash",
            )
        }
    }

    @Test
    fun test_invalid_neither_branchId_nor_versionHash() {
        // Invalid: must specify either branchId or versionHash
        assertFailsWith<IllegalArgumentException> {
            ReplicatedModelParameters(
                repositoryId = "repo",
                branchId = null,
                idScheme = IdSchemeJS.MODELIX,
            )
        }
    }

    @Test
    fun test_invalid_versionHash_without_readonly() {
        // Invalid: versionHash requires readonly=true
        assertFailsWith<IllegalArgumentException> {
            ReplicatedModelParameters(
                repositoryId = "repo",
                branchId = null,
                idScheme = IdSchemeJS.MODELIX,
                readonly = false,
                versionHash = "hash",
            )
        }
    }

    @Test
    fun test_equals_same_versionAttributes_supplier() {
        val supplier: () -> Array<AttributeEntryJS> = { arrayOf(AttributeEntryJS("env", "ci")) }
        val a = ReplicatedModelParameters(
            repositoryId = "repo",
            branchId = "branch",
            idScheme = IdSchemeJS.MODELIX,
            versionAttributes = supplier,
        )
        val b = ReplicatedModelParameters(
            repositoryId = "repo",
            branchId = "branch",
            idScheme = IdSchemeJS.MODELIX,
            versionAttributes = supplier,
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertSame(a.versionAttributes, b.versionAttributes)
    }

    @Test
    fun test_not_equals_different_versionAttributes_suppliers() {
        // Two distinct lambdas with the same body are not equal — reference equality applies.
        val a = ReplicatedModelParameters(
            repositoryId = "repo",
            branchId = "branch",
            idScheme = IdSchemeJS.MODELIX,
            versionAttributes = { arrayOf(AttributeEntryJS("env", "ci")) },
        )
        val b = ReplicatedModelParameters(
            repositoryId = "repo",
            branchId = "branch",
            idScheme = IdSchemeJS.MODELIX,
            versionAttributes = { arrayOf(AttributeEntryJS("env", "ci")) },
        )
        assertNotEquals(a, b)
    }
}
