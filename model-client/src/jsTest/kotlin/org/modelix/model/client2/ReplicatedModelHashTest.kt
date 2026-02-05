package org.modelix.model.client2

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReplicatedModelHashTest {

    @Test
    fun ReplicatedModelParameters_validation() {
        // Valid: branchId
        // branchId is positional 2nd arg.
        ReplicatedModelParameters("repo", "branch", IdSchemeJS.MODELIX)

        // Valid: versionHash
        // branchId must be null.
        ReplicatedModelParameters(
            repositoryId = "repo",
            branchId = null,
            idScheme = IdSchemeJS.MODELIX,
            versionHash = "hash",
        )

        // Invalid: both branchId and versionHash
        assertFailsWith<IllegalArgumentException> {
            ReplicatedModelParameters(
                repositoryId = "repo",
                branchId = "branch",
                idScheme = IdSchemeJS.MODELIX,
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
    }
}
