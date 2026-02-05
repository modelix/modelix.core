package org.modelix.model.client2

import kotlin.test.Test
import kotlin.test.assertFailsWith

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
}
