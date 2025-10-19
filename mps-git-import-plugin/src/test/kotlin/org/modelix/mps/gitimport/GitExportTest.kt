package org.modelix.mps.gitimport

import com.intellij.util.io.ZipUtil
import com.intellij.util.io.delete
import org.modelix.datastructures.model.ChildrenChangedEvent
import org.modelix.datastructures.model.ConceptChangedEvent
import org.modelix.datastructures.model.ContainmentChangedEvent
import org.modelix.datastructures.model.NodeAddedEvent
import org.modelix.datastructures.model.NodeRemovedEvent
import org.modelix.datastructures.model.PropertyChangedEvent
import org.modelix.datastructures.model.ReferenceChangedEvent
import org.modelix.model.IVersion
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.getDescendants
import org.modelix.model.api.getName
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWriteOnModel
import org.modelix.model.historyAsSequence
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mutable.asModelSingleThreaded
import org.modelix.streams.getSuspending
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute

class GitExportTest : MPSTestBase() {

    fun `test export`() = runWithModelServer { port ->
        val modelServerUrl = "http://localhost:$port"
        val repositoryId = RepositoryId("git-import-test")
        val modelixBranchInitialImport = repositoryId.getBranchReference("git-import-result")
        val modelixBranchModified = repositoryId.getBranchReference("modified")
        val modelixBranchReimport = repositoryId.getBranchReference("reimport")
        val gitDir = extractTestProject()

        // create some initial import
        GitImporter(
            gitDir = gitDir.toFile(),
            modelServerUrl = modelServerUrl,
            baseBranch = repositoryId.getBranchReference(),
            targetBranchName = modelixBranchInitialImport.branchName,
            gitRevision = "main",
        ).runSuspending()

        val client = ModelClientV2.builder().url(modelServerUrl).lazyAndBlockingQueries().build()
        assertEquals(setOf(repositoryId), client.listRepositories().toSet())
        assertEquals(
            setOf(repositoryId.getBranchReference(), modelixBranchInitialImport),
            client.listBranches(repositoryId).toSet(),
        )

        // create new branch for the user to work on
        client.push(modelixBranchModified, client.pull(modelixBranchInitialImport, null), null)

        // make some changes
        val modifiedVersion = client.runWriteOnModel(modelixBranchModified) { rootNode ->
            val nameProperty = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.toReference()
            rootNode.getDescendants(true).forEach { node ->

                // generator name is derived from the language name
                if (node.getConceptReference() == BuiltinLanguages.MPSRepositoryConcepts.Generator.getReference()) return@forEach

                // the project name is used for the node ID and renaming it isn't supported
                if (node.getConceptReference() == BuiltinLanguages.MPSRepositoryConcepts.Project.getReference()) return@forEach

                val name = node.getPropertyValue(nameProperty)
                if (name != null) {
                    node.setPropertyValue(nameProperty, name + "Changed")
                }
            }
        }

        // export changes to git
        val exporter = GitExporter(
            gitDir = gitDir.toFile(),
            modelServerUrl = modelServerUrl,
            modelixBranch = modelixBranchModified,
            modelixVersionHash = modifiedVersion.getContentHash(),
            gitBranch = "export",
            token = null,
        )
        exporter.runSuspending()

        // re-import
        GitImporter(
            gitDir = gitDir.toFile(),
            modelServerUrl = modelServerUrl,
            baseBranch = modelixBranchInitialImport,
            targetBranchName = modelixBranchReimport.branchName,
            gitRevision = "export",
        ).runSuspending()

        val reimportedVersion = client.pull(modelixBranchReimport, null)

        // check that no changes got lost during the round-trip
        val diff = reimportedVersion.getModelTree().getChanges(modifiedVersion.getModelTree(), false).toList().getSuspending(reimportedVersion.asObject().graph)
        val changes = ArrayList<String>()
        for (event in diff) {
            when (event) {
                is ConceptChangedEvent<INodeReference> -> TODO()
                is ContainmentChangedEvent<INodeReference> -> TODO()
                is NodeAddedEvent<INodeReference> -> TODO()
                is NodeRemovedEvent<INodeReference> -> TODO()
                is ChildrenChangedEvent<INodeReference> -> TODO()
                is PropertyChangedEvent<INodeReference> -> {
                    changes += modifiedVersion.getModelTree().getProperty(event.nodeId, event.role).getSuspending(modifiedVersion.asObject().graph) +
                        " -> " +
                        reimportedVersion.getModelTree().getProperty(event.nodeId, event.role).getSuspending(reimportedVersion.asObject().graph)
                }
                is ReferenceChangedEvent<INodeReference> -> TODO()
            }
        }
        println(changes.joinToString("\n"))
        assertEmpty(diff)
    }

    private fun assertCommit(latestVersion: IVersion, id: String, message: String, expected: String) {
        val versionsByGitCommit = latestVersion.historyAsSequence().associateBy { it.gitCommit }
        val version = versionsByGitCommit[id]!!
        kotlin.test.assertEquals(message, version.getAttributes()["git-message"]?.trim())
        val model = version.getModelTree()
        val repositoryNode = model.asModelSingleThreaded().getRootNode()
        kotlin.test.assertEquals(expected, nodeToText(repositoryNode))
    }

    private fun nodeToText(node: IReadableNode, depth: Int = 0): String {
        val nodeLine = node.getName() ?: ""
        val allChildren = node.getAllChildren()

        // sort modules, models and root nodes only
        val sortedChildren = if (depth <= 2) allChildren.sortedBy { it.getName() } else allChildren

        val children = sortedChildren.map { nodeToText(it, depth + 1) }.filter { it.isNotEmpty() }
        return if (nodeLine.isEmpty()) {
            children.joinToString("\n")
        } else {
            listOf(nodeLine).plus(children.map { it.prependIndent("  ") }).joinToString("\n")
        }
    }

    fun runWithImportResult(body: (gitDir: Path, modelServerUrl: String) -> Unit) = runWithModelServer { port ->

        val modelServerUrl = "http://localhost:$port"
        val repositoryId = RepositoryId("git-import-test")
        val targetBranchName = "git-import-result"
        val targetBranch = repositoryId.getBranchReference(targetBranchName)
        val gitDir = extractTestProject()
        val importer = GitImporter(
            gitDir = gitDir.toFile(),
            modelServerUrl = modelServerUrl,
            baseBranch = repositoryId.getBranchReference(),
            targetBranchName = targetBranchName,
            gitRevision = "main",
        )

        importer.runSuspending()

        val client = ModelClientV2.builder().url(modelServerUrl).lazyAndBlockingQueries().build()
        assertEquals(setOf(repositoryId), client.listRepositories().toSet())
        assertEquals(
            setOf(repositoryId.getBranchReference(), targetBranch),
            client.listBranches(repositoryId).toSet(),
        )

        val latestVersion = client.pull(targetBranch, null)
        body(gitDir, modelServerUrl)
    }

    private fun extractTestProject(): Path {
        val projectDirParent = Path.of("build", "test-projects").absolute()
        projectDirParent.toFile().mkdirs()
        val projectDir = Files.createTempDirectory(projectDirParent, "git-import-test")
        projectDir.delete(recursively = true)
        projectDir.toFile().mkdirs()
        projectDir.toFile().deleteOnExit()
        ZipUtil.extract(Path.of("testdata", "git-import-test-repo.zip"), projectDir, null)
        return projectDir.resolve("git-import-test-repo")
    }
}
