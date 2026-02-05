package org.modelix.model.client2

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReplicatedModelHashTest {

    @Test
    fun ReplicatedModelParameters_validation() {
        // Valid: branchId
        // branchId is positional 2nd arg.
        ReplicatedModelParameters("repo", "branch", IdSchemeJS.MODELIX)

        // Valid: versionHash (must be readonly)
        // branchId must be null.
        ReplicatedModelParameters(
            repositoryId = "repo",
            branchId = null,
            idScheme = IdSchemeJS.MODELIX,
            readonly = true,
            versionHash = "hash",
        )

        // Invalid: both branchId and versionHash
        assertFailsWith<IllegalArgumentException> {
            ReplicatedModelParameters(
                repositoryId = "repo",
                branchId = "branch",
                idScheme = IdSchemeJS.MODELIX,
                readonly = true,
                versionHash = "hash",
            )
        }

        // Invalid: neither
        assertFailsWith<IllegalArgumentException> {
            ReplicatedModelParameters(
                repositoryId = "repo",
                branchId = null,
                idScheme = IdSchemeJS.MODELIX,
            )
        }

        // Invalid: versionHash but not readonly
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
