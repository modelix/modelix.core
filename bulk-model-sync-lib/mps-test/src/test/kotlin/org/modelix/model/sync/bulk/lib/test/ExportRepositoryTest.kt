package org.modelix.model.sync.bulk.lib.test
import com.google.common.jimfs.Jimfs
import org.modelix.mps.model.sync.bulk.MPSBulkSynchronizer
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class ExportRepositoryTest : MPSTestBase() {

    private val fileSystem = Jimfs.newFileSystem()
    private val outputPath = fileSystem.getPath("/some/output/path")

    fun `test exports module included by name (testdata oneEmptyModule)`() {
        MPSBulkSynchronizer.exportModulesFromRepository(mpsProject.repository, setOf("onlySolution"), emptySet(), outputPath)

        assertEquals(listOf("onlySolution.json"), outputPath.listDirectoryEntries().map { it.name })
    }

    fun `test exports module included by prefix (testdata oneEmptyModule)`() {
        MPSBulkSynchronizer.exportModulesFromRepository(mpsProject.repository, emptySet(), setOf("onlySol"), outputPath)

        assertEquals(listOf("onlySolution.json"), outputPath.listDirectoryEntries().map { it.name })
    }

    fun `test fail if no module is found (testdata oneEmptyModule)`() {
        val expectedMsg = """
            No module matched the inclusion criteria.
            [includedModules] = [someSolution]
            [includedModulePrefixes] = [somePrefix]
        """.trimIndent()

        try {
            MPSBulkSynchronizer
                .exportModulesFromRepository(mpsProject.repository, setOf("someSolution"), setOf("somePrefix"), outputPath)
            fail("Exporting models should fail.")
        } catch (ex: IllegalArgumentException) {
            assertEquals(expectedMsg, ex.message)
        } catch (ex: Throwable) {
            fail("Expected IllegalArgumentException but got $ex")
        }
    }
}
