package org.modelix.mps.sync3

import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import org.jdom.Element
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModelSyncServiceTest {

    private fun createMockProject(): Project {
        val mpsProject = mockk<MPSProject>(relaxed = true)
        val project = mockk<Project>(relaxed = true)
        every { ProjectHelper.fromIdeaProject(project) } returns mpsProject
        return project
    }

    @Test
    fun `updateBinding updates branch reference for matching binding without resetting state`() {
        // Given
        val service = ModelSyncService(createMockProject())
        val repositoryId = RepositoryId("test-repo")
        val oldBranchRef = BranchReference(repositoryId, "old-branch")
        val newBranchRef = BranchReference(repositoryId, "new-branch")

        val connectionProps = ModelServerConnectionProperties(
            url = "http://localhost:8080",
            repositoryId = repositoryId,
        )

        // Create initial state with a binding using the old branch reference (disabled to avoid worker creation)
        val initialState = Element("model-sync").apply {
            addContent(
                Element("binding").apply {
                    addContent(Element("enabled").apply { text = "false" })
                    addContent(
                        Element("url").apply {
                            text = connectionProps.url
                            setAttribute("repositoryScoped", "true")
                        },
                    )
                    addContent(Element("repository").apply { text = repositoryId.id })
                    addContent(Element("branch").apply { text = oldBranchRef.branchName })
                    addContent(Element("versionHash").apply { text = "hash123" })
                },
            )
        }

        service.loadState(initialState)

        // When
        service.updateBinding(oldBranchRef, newBranchRef, resetLocalState = false)

        // Then
        val bindings = service.getBindings()
        assertEquals(1, bindings.size, "Should have exactly one binding")

        val binding = bindings.first()
        assertEquals(newBranchRef, binding.getBranchRef(), "Branch reference should be updated")
    }

    @Test
    fun `updateBinding does not affect bindings with different branch references`() {
        // Given
        val service = ModelSyncService(createMockProject())
        val repositoryId1 = RepositoryId("test-repo-1")
        val repositoryId2 = RepositoryId("test-repo-2")
        val oldBranchRef = BranchReference(repositoryId1, "old-branch")
        val newBranchRef = BranchReference(repositoryId1, "new-branch")
        val unchangedBranchRef = BranchReference(repositoryId2, "unchanged-branch")

        val connectionProps1 = ModelServerConnectionProperties(
            url = "http://localhost:8080",
            repositoryId = repositoryId1,
        )
        val connectionProps2 = ModelServerConnectionProperties(
            url = "http://localhost:8080",
            repositoryId = repositoryId2,
        )

        // Create initial state with two bindings (disabled to avoid worker creation)
        val initialState = Element("model-sync").apply {
            addContent(
                Element("binding").apply {
                    addContent(Element("enabled").apply { text = "false" })
                    addContent(
                        Element("url").apply {
                            text = connectionProps1.url
                            setAttribute("repositoryScoped", "true")
                        },
                    )
                    addContent(Element("repository").apply { text = repositoryId1.id })
                    addContent(Element("branch").apply { text = oldBranchRef.branchName })
                    addContent(Element("versionHash").apply { text = "hash123" })
                },
            )
            addContent(
                Element("binding").apply {
                    addContent(Element("enabled").apply { text = "false" })
                    addContent(
                        Element("url").apply {
                            text = connectionProps2.url
                            setAttribute("repositoryScoped", "true")
                        },
                    )
                    addContent(Element("repository").apply { text = repositoryId2.id })
                    addContent(Element("branch").apply { text = unchangedBranchRef.branchName })
                    addContent(Element("versionHash").apply { text = "hash456" })
                },
            )
        }

        service.loadState(initialState)

        // When
        service.updateBinding(oldBranchRef, newBranchRef, resetLocalState = false)

        // Then
        val bindings = service.getBindings()
        assertEquals(2, bindings.size, "Should have exactly two bindings")

        val updatedBinding = bindings.find { it.getBranchRef().repositoryId == repositoryId1 }
        assertNotNull(updatedBinding, "Updated binding should exist")
        assertEquals(newBranchRef, updatedBinding.getBranchRef(), "Branch reference should be updated")

        val unchangedBinding = bindings.find { it.getBranchRef().repositoryId == repositoryId2 }
        assertNotNull(unchangedBinding, "Unchanged binding should exist")
        assertEquals(unchangedBranchRef, unchangedBinding.getBranchRef(), "Branch reference should remain unchanged")
    }

    @Test
    fun `updateBinding preserves binding state when updating branch reference`() {
        // Given
        val service = ModelSyncService(createMockProject())
        val repositoryId = RepositoryId("test-repo")
        val oldBranchRef = BranchReference(repositoryId, "old-branch")
        val newBranchRef = BranchReference(repositoryId, "new-branch")

        val connectionProps = ModelServerConnectionProperties(
            url = "http://localhost:8080",
            repositoryId = repositoryId,
        )

        val expectedVersionHash = "preserved-hash-123"

        // Create initial state with a binding (disabled to avoid worker creation)
        val initialState = Element("model-sync").apply {
            addContent(
                Element("binding").apply {
                    addContent(Element("enabled").apply { text = "false" })
                    addContent(
                        Element("url").apply {
                            text = connectionProps.url
                            setAttribute("repositoryScoped", "true")
                        },
                    )
                    addContent(Element("repository").apply { text = repositoryId.id })
                    addContent(Element("branch").apply { text = oldBranchRef.branchName })
                    addContent(Element("versionHash").apply { text = expectedVersionHash })
                },
            )
        }

        service.loadState(initialState)

        // When
        service.updateBinding(oldBranchRef, newBranchRef, resetLocalState = false)

        // Then
        val state = service.getState()
        assertNotNull(state, "State should not be null")

        val bindings = state.getChildren("binding")
        assertEquals(1, bindings.size, "Should have exactly one binding")

        val binding = bindings.first()
        assertEquals(newBranchRef.branchName, binding.getChildText("branch"), "Branch name should be updated")
        assertEquals(expectedVersionHash, binding.getChildText("versionHash"), "Version hash should be preserved")
        assertEquals("false", binding.getChildText("enabled"), "Enabled state should be preserved")
    }

    @Test
    fun `updateBinding throws exception when no matching binding exists`() {
        // Given
        val service = ModelSyncService(createMockProject())
        val repositoryId = RepositoryId("test-repo")
        val nonExistentBranchRef = BranchReference(repositoryId, "non-existent")
        val newBranchRef = BranchReference(repositoryId, "new-branch")
        val existingBranchRef = BranchReference(repositoryId, "existing-branch")

        val connectionProps = ModelServerConnectionProperties(
            url = "http://localhost:8080",
            repositoryId = repositoryId,
        )

        // Create initial state with a binding that doesn't match (disabled to avoid worker creation)
        val initialState = Element("model-sync").apply {
            addContent(
                Element("binding").apply {
                    addContent(Element("enabled").apply { text = "false" })
                    addContent(
                        Element("url").apply {
                            text = connectionProps.url
                            setAttribute("repositoryScoped", "true")
                        },
                    )
                    addContent(Element("repository").apply { text = repositoryId.id })
                    addContent(Element("branch").apply { text = existingBranchRef.branchName })
                    addContent(Element("versionHash").apply { text = "hash123" })
                },
            )
        }

        service.loadState(initialState)

        // When/Then - trying to update a branch that doesn't exist should throw an exception
        val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
            service.updateBinding(nonExistentBranchRef, newBranchRef, resetLocalState = false)
        }

        assertTrue(
            exception.message?.contains("No binding for") == true,
            "Exception message should mention 'No binding for'",
        )

        // Verify existing binding remains unchanged after the failed update
        val bindings = service.getBindings()
        assertEquals(1, bindings.size, "Should still have exactly one binding")
        assertEquals(existingBranchRef, bindings.first().getBranchRef(), "Existing branch reference should be unchanged")
    }

    @Test
    fun `updateBinding throws exception when no bindings exist at all`() {
        // Given
        val service = ModelSyncService(createMockProject())
        val repositoryId = RepositoryId("test-repo")
        val oldBranchRef = BranchReference(repositoryId, "old-branch")
        val newBranchRef = BranchReference(repositoryId, "new-branch")

        // Start with an empty state - no bindings
        val initialState = Element("model-sync")
        service.loadState(initialState)

        // When/Then - trying to update when no bindings exist should throw an exception
        val exception = kotlin.test.assertFailsWith<IllegalArgumentException> {
            service.updateBinding(oldBranchRef, newBranchRef, resetLocalState = false)
        }

        assertTrue(
            exception.message?.contains("No binding for") == true,
            "Exception message should mention 'No binding for'",
        )

        // Verify no bindings were created
        val bindings = service.getBindings()
        assertEquals(0, bindings.size, "Should have no bindings")
    }

    @Test
    fun `updateBinding with resetLocalState true clears version hash`() {
        // Given
        val service = ModelSyncService(createMockProject())
        val repositoryId = RepositoryId("test-repo")
        val oldBranchRef = BranchReference(repositoryId, "old-branch")
        val newBranchRef = BranchReference(repositoryId, "new-branch")

        val connectionProps = ModelServerConnectionProperties(
            url = "http://localhost:8080",
            repositoryId = repositoryId,
        )

        val originalVersionHash = "original-hash-123"

        // Create initial state with a binding that has a version hash
        val initialState = Element("model-sync").apply {
            addContent(
                Element("binding").apply {
                    addContent(Element("enabled").apply { text = "false" })
                    addContent(
                        Element("url").apply {
                            text = connectionProps.url
                            setAttribute("repositoryScoped", "true")
                        },
                    )
                    addContent(Element("repository").apply { text = repositoryId.id })
                    addContent(Element("branch").apply { text = oldBranchRef.branchName })
                    addContent(Element("versionHash").apply { text = originalVersionHash })
                },
            )
        }

        service.loadState(initialState)

        // When
        service.updateBinding(oldBranchRef, newBranchRef, resetLocalState = true)

        // Then
        val state = service.getState()
        assertNotNull(state, "State should not be null")

        val bindings = state.getChildren("binding")
        assertEquals(1, bindings.size, "Should have exactly one binding")

        val binding = bindings.first()
        assertEquals(newBranchRef.branchName, binding.getChildText("branch"), "Branch name should be updated")
        assertEquals("", binding.getChildText("versionHash"), "Version hash should be cleared (null/empty)")
        assertEquals("false", binding.getChildText("enabled"), "Enabled state should be preserved")
    }

    @Test
    fun `updateBinding with resetLocalState false preserves version hash`() {
        // Given
        val service = ModelSyncService(createMockProject())
        val repositoryId = RepositoryId("test-repo")
        val oldBranchRef = BranchReference(repositoryId, "old-branch")
        val newBranchRef = BranchReference(repositoryId, "new-branch")

        val connectionProps = ModelServerConnectionProperties(
            url = "http://localhost:8080",
            repositoryId = repositoryId,
        )

        val originalVersionHash = "original-hash-456"

        // Create initial state with a binding that has a version hash
        val initialState = Element("model-sync").apply {
            addContent(
                Element("binding").apply {
                    addContent(Element("enabled").apply { text = "false" })
                    addContent(
                        Element("url").apply {
                            text = connectionProps.url
                            setAttribute("repositoryScoped", "true")
                        },
                    )
                    addContent(Element("repository").apply { text = repositoryId.id })
                    addContent(Element("branch").apply { text = oldBranchRef.branchName })
                    addContent(Element("versionHash").apply { text = originalVersionHash })
                },
            )
        }

        service.loadState(initialState)

        // When
        service.updateBinding(oldBranchRef, newBranchRef, resetLocalState = false)

        // Then
        val state = service.getState()
        assertNotNull(state, "State should not be null")

        val bindings = state.getChildren("binding")
        assertEquals(1, bindings.size, "Should have exactly one binding")

        val binding = bindings.first()
        assertEquals(newBranchRef.branchName, binding.getChildText("branch"), "Branch name should be updated")
        assertEquals(originalVersionHash, binding.getChildText("versionHash"), "Version hash should be preserved")
        assertEquals("false", binding.getChildText("enabled"), "Enabled state should be preserved")
    }

    @Test
    fun `updateBinding with resetLocalState true only affects matching binding`() {
        // Given
        val service = ModelSyncService(createMockProject())
        val repositoryId1 = RepositoryId("test-repo-1")
        val repositoryId2 = RepositoryId("test-repo-2")
        val oldBranchRef = BranchReference(repositoryId1, "old-branch")
        val newBranchRef = BranchReference(repositoryId1, "new-branch")
        val unchangedBranchRef = BranchReference(repositoryId2, "unchanged-branch")

        val connectionProps1 = ModelServerConnectionProperties(
            url = "http://localhost:8080",
            repositoryId = repositoryId1,
        )
        val connectionProps2 = ModelServerConnectionProperties(
            url = "http://localhost:8080",
            repositoryId = repositoryId2,
        )

        val versionHash1 = "hash-to-be-cleared"
        val versionHash2 = "hash-to-be-preserved"

        // Create initial state with two bindings
        val initialState = Element("model-sync").apply {
            addContent(
                Element("binding").apply {
                    addContent(Element("enabled").apply { text = "false" })
                    addContent(
                        Element("url").apply {
                            text = connectionProps1.url
                            setAttribute("repositoryScoped", "true")
                        },
                    )
                    addContent(Element("repository").apply { text = repositoryId1.id })
                    addContent(Element("branch").apply { text = oldBranchRef.branchName })
                    addContent(Element("versionHash").apply { text = versionHash1 })
                },
            )
            addContent(
                Element("binding").apply {
                    addContent(Element("enabled").apply { text = "false" })
                    addContent(
                        Element("url").apply {
                            text = connectionProps2.url
                            setAttribute("repositoryScoped", "true")
                        },
                    )
                    addContent(Element("repository").apply { text = repositoryId2.id })
                    addContent(Element("branch").apply { text = unchangedBranchRef.branchName })
                    addContent(Element("versionHash").apply { text = versionHash2 })
                },
            )
        }

        service.loadState(initialState)

        // When - reset state only for the updated binding
        service.updateBinding(oldBranchRef, newBranchRef, resetLocalState = true)

        // Then
        val state = service.getState()
        assertNotNull(state, "State should not be null")

        val bindings = state.getChildren("binding")
        assertEquals(2, bindings.size, "Should have exactly two bindings")

        // Find the updated binding
        val updatedBinding = bindings.find { it.getChildText("branch") == newBranchRef.branchName }
        assertNotNull(updatedBinding, "Updated binding should exist")
        assertEquals(newBranchRef.branchName, updatedBinding.getChildText("branch"), "Branch name should be updated")
        assertEquals("", updatedBinding.getChildText("versionHash"), "Version hash should be cleared for updated binding")

        // Find the unchanged binding
        val unchangedBinding = bindings.find { it.getChildText("branch") == unchangedBranchRef.branchName }
        assertNotNull(unchangedBinding, "Unchanged binding should exist")
        assertEquals(unchangedBranchRef.branchName, unchangedBinding.getChildText("branch"), "Branch name should remain unchanged")
        assertEquals(versionHash2, unchangedBinding.getChildText("versionHash"), "Version hash should be preserved for unchanged binding")
    }
}
