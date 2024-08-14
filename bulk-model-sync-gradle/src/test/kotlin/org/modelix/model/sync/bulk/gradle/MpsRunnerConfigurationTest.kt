package org.modelix.model.sync.bulk.gradle

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.modelix.model.sync.bulk.gradle.config.LocalTarget
import org.modelix.model.sync.bulk.gradle.config.ServerSource
import org.modelix.model.sync.bulk.gradle.config.SyncDirection
import java.io.File
import kotlin.test.Test

class MpsRunnerConfigurationTest {

    private val jsonDir = File("/jsonDir")
    private val classPathElements = emptySet<File>()

    @Test
    fun `build configuration for local target with a server source without a base revision`() {
        val serverSource = ServerSource(
            url = "aUrl",
            repositoryId = "aRepositoryId",
            branchName = "aBranchName",
        )
        val localTarget = LocalTarget(repositoryDir = File("/repositoryDir"))
        val syncDirection = SyncDirection("syncDirection", serverSource, localTarget)

        val config = buildMpsRunConfigurationForLocalTarget(syncDirection, classPathElements, jsonDir)
        val jvmArgs = config.jvmArgs

        val expectedJvmArgs = listOf(
            "-Dmodelix.mps.model.sync.bulk.input.path=/jsonDir",
            "-Dmodelix.mps.model.sync.bulk.input.modules=",
            "-Dmodelix.mps.model.sync.bulk.input.modules.prefixes=",
            "-Dmodelix.mps.model.sync.bulk.repo.path=/repositoryDir",
            "-Dmodelix.mps.model.sync.bulk.input.continueOnError=false",
            "-Xmx2g",
        )

        jvmArgs shouldContainExactlyInAnyOrder expectedJvmArgs
    }

    @Test
    fun `build configuration for local target with a server source with a base revision and branch name`() {
        val serverSource = ServerSource(
            url = "aUrl",
            repositoryId = "aRepositoryId",
            branchName = "aBranchName",
            baseRevision = "aBaseRevision",
        )
        val localTarget = LocalTarget(repositoryDir = File("/repositoryDir"))
        val syncDirection = SyncDirection("syncDirection", serverSource, localTarget)

        val config = buildMpsRunConfigurationForLocalTarget(syncDirection, classPathElements, jsonDir)
        val jvmArgs = config.jvmArgs

        val expectedJvmArgs = listOf(
            "-Dmodelix.mps.model.sync.bulk.input.path=/jsonDir",
            "-Dmodelix.mps.model.sync.bulk.input.modules=",
            "-Dmodelix.mps.model.sync.bulk.input.modules.prefixes=",
            "-Dmodelix.mps.model.sync.bulk.repo.path=/repositoryDir",
            "-Dmodelix.mps.model.sync.bulk.input.continueOnError=false",
            "-Xmx2g",
            "-Dmodelix.mps.model.sync.bulk.server.repository=aRepositoryId",
            "-Dmodelix.mps.model.sync.bulk.server.version.hash=null",
            "-Dmodelix.mps.model.sync.bulk.server.url=aUrl",
            "-Dmodelix.mps.model.sync.bulk.server.version.base.hash=aBaseRevision",
        )

        jvmArgs shouldContainExactlyInAnyOrder expectedJvmArgs
    }

    @Test
    fun `build configuration for local target with a server source with a base revision and revision`() {
        val serverSource = ServerSource(
            url = "aUrl",
            repositoryId = "aRepositoryId",
            revision = "aRevisionToSync",
            baseRevision = "aBaseRevision",
        )
        val localTarget = LocalTarget(repositoryDir = File("/repositoryDir"))
        val syncDirection = SyncDirection("syncDirection", serverSource, localTarget)

        val config = buildMpsRunConfigurationForLocalTarget(syncDirection, classPathElements, jsonDir)
        val jvmArgs = config.jvmArgs

        val expectedJvmArgs = listOf(
            "-Dmodelix.mps.model.sync.bulk.input.path=/jsonDir",
            "-Dmodelix.mps.model.sync.bulk.input.modules=",
            "-Dmodelix.mps.model.sync.bulk.input.modules.prefixes=",
            "-Dmodelix.mps.model.sync.bulk.repo.path=/repositoryDir",
            "-Dmodelix.mps.model.sync.bulk.input.continueOnError=false",
            "-Xmx2g",
            "-Dmodelix.mps.model.sync.bulk.server.repository=aRepositoryId",
            "-Dmodelix.mps.model.sync.bulk.server.url=aUrl",
            "-Dmodelix.mps.model.sync.bulk.server.version.base.hash=aBaseRevision",
            "-Dmodelix.mps.model.sync.bulk.server.version.hash=aRevisionToSync",
        )

        jvmArgs shouldContainExactlyInAnyOrder expectedJvmArgs
    }
}
