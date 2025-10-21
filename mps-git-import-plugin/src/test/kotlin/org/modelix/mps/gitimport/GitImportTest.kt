package org.modelix.mps.gitimport

import com.intellij.util.io.ZipUtil
import com.intellij.util.io.delete
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.model.IVersion
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.getName
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.historyAsSequence
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.lazy.diff
import org.modelix.model.mutable.asModelSingleThreaded
import org.modelix.streams.getBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute

class GitImportTest : MPSTestBase() {

    private fun assertCommit(latestVersion: IVersion, id: String, message: String, expected: String) {
        val versionsByGitCommit = latestVersion.historyAsSequence().associateBy { it.gitCommit }
        val version = versionsByGitCommit[id]!!
        kotlin.test.assertEquals(message, version.getAttributes()["git-message"]?.trim())
        val model = version.getModelTree()
        val repositoryNode = model.asModelSingleThreaded().getRootNode()
        kotlin.test.assertEquals(expected, nodeToText(repositoryNode))
    }

    fun `test commit ID of latest version`() = runWithImportResult { version ->
        assertEquals("153318b1deac5ad0d3e351d624042e6d396005a9", version.gitCommit)
    }

    fun `test all imported versions have a commit ID`() = runWithImportResult { version ->
        val actual = version.historyAsSequence().map { it.gitCommit }.joinToString("\n")
        val expected = listOf(
            "153318b1deac5ad0d3e351d624042e6d396005a9",
            "6ec546f1cc0d160c87da3506002a3cd2fceeea9d",
            "6cc9f6aa0c2baea58ab5ddb62467a23893f9bed3",
            "ed7a6059a6321225dcecc44f1ca852733247b4f3",
            "0132341d41271e434515213f98617696251ff91f",
            "50f8737a7b523e776476e9af5b4a3b910bf49efe",
            "6147302470dc2b7a50992cf02c83bb1cf3d32b98",
            "790114700267b698cb46dc9999e66b51a1ba17db",
            "96a0ec86422c5be64d53907195b0dec5f278234e",
            "8c2fbbb78f4ef124dad3b8fd2d634efb32afd5e8",
            "618c468296c3387afe9782c16368f7106dbeae2e",
            "a826aeb58214c52b324ebd1b83d9db0ceb75abca",
            "e6648030f745cb55eb6d93bc93a8657fa56f1764",
            "aac3b37b4c6213f8057961a79814989b2cbf3007",
            "6f73ef5a56b9dd6a8c6140f1369fbf93de4046e5",
            "079eb7f15e1c24dccf35d281fd197f157cb7a10f",
            "bcb8a4d9ca158e0c77cdb875f9594b154efdf061",
            "389ccb51b6fb49656fcb6bca29235bdf8434fec7",
            "5a2823ce182dc8419b7e06d2513407fcfd719c56",
            "4838fd8d3c84979337405233f7046f52209bd6ff",
            "ae84b8b26b84b3d6e551a2608deafa75f5258993",
            null,
        ).joinToString("\n")
        assertEquals(expected, actual)
    }

    fun `test file per root persistence can be imported`() = runWithImportResult { version ->
        val versionsByGitCommit = version.historyAsSequence().associateBy { it.gitCommit }

        val firstGitCommit = versionsByGitCommit["ae84b8b26b84b3d6e551a2608deafa75f5258993"]!!
        val model = firstGitCommit.getModelTree()
        val repositoryNode = model.asModelSingleThreaded().getRootNode()
        val solutionNode = repositoryNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference())
            .find { it.getName() == "my.solution" }
        kotlin.test.assertNotNull(solutionNode, "Solution node not found")
        val allModels = solutionNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models.toReference()).associateBy { it.getName() }
        val modelNode = solutionNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models.toReference())
            .find { it.getName() == "my.solution.model_with_file_per_root_persistence" }
        kotlin.test.assertNotNull(modelNode, "Model node not found, ${allModels.keys}")
        val rootNode = modelNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.toReference())
            .find { it.getName() == "MyClass" }
        kotlin.test.assertNotNull(rootNode, "Root node not found")
    }

    fun `test result of initial commit`() = runWithImportResult { version ->
        assertCommit(
            version,
            "ae84b8b26b84b3d6e551a2608deafa75f5258993",
            "Initial Commit",
            """
                my.devkit
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    MyClass
                      f
                test.org.modelix.git.import
            """.trimIndent(),
        )
    }
    fun `test result after README change`() = runWithImportResult { version ->
        assertCommit(
            version,
            "4838fd8d3c84979337405233f7046f52209bd6ff",
            "Added README",
            """
                my.devkit
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    MyClass
                      f
                test.org.modelix.git.import
            """.trimIndent(),
        )

        assertCommit(
            version,
            "5a2823ce182dc8419b7e06d2513407fcfd719c56",
            "feat: added a root node in a file_per_root persistence model",
            """
                my.devkit
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    AnotherRootNode
                      m1
                      m2
                      m3
                    MyClass
                      f
                test.org.modelix.git.import
            """.trimIndent(),
        )

        assertCommit(
            version,
            "389ccb51b6fb49656fcb6bca29235bdf8434fec7",
            "feat: added a root node in a binary persistence model",
            """
                my.devkit
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    AnotherRootNode
                      m1
                      m2
                      m3
                    MyClass
                      f
                test.org.modelix.git.import
            """.trimIndent(),
        )

        assertCommit(
            version,
            "bcb8a4d9ca158e0c77cdb875f9594b154efdf061",
            "Feat(feature-A): Add utils model and enhance devkit",
            """
                my.devkit
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    AnotherRootNode
                      m1
                      m2
                      m3
                    MyClass
                      f
                  my.solution.model_with_regular_xml_persistence
                test.org.modelix.git.import
            """.trimIndent(),
        )

        assertCommit(
            version,
            "153318b1deac5ad0d3e351d624042e6d396005a9",
            "Docs: Update README by another author",
            """
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    MyRenamedClassInFilePerRootPersistence
                      f
                      modifiedRootNodeOnMainBranch
                    NewRootNodeInFilePerRootPersistence
                  my.solution.model_with_regular_xml_persistence
                    AddedRootToRegularPersistence
                my.solution.added.on.feature.branch.b.renamed
                  my.solution.added.on.feature.branch.b.renamed.a_model
                    NewRootNodeOnFeatureBranchB
                      abc
                      def
                test.org.modelix.git.import
            """.trimIndent(),
        )
    }
    fun `test result after adding root with file_per_root persistence`() = runWithImportResult { version ->
        assertCommit(
            version,
            "5a2823ce182dc8419b7e06d2513407fcfd719c56",
            "feat: added a root node in a file_per_root persistence model",
            """
                my.devkit
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    AnotherRootNode
                      m1
                      m2
                      m3
                    MyClass
                      f
                test.org.modelix.git.import
            """.trimIndent(),
        )
    }
    fun `test result after adding root with binary persistence`() = runWithImportResult { version ->
        assertCommit(
            version,
            "389ccb51b6fb49656fcb6bca29235bdf8434fec7",
            "feat: added a root node in a binary persistence model",
            """
                my.devkit
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    AnotherRootNode
                      m1
                      m2
                      m3
                    MyClass
                      f
                test.org.modelix.git.import
            """.trimIndent(),
        )
    }
    fun `test result of final commit`() = runWithImportResult { version ->
        assertCommit(
            version,
            "153318b1deac5ad0d3e351d624042e6d396005a9",
            "Docs: Update README by another author",
            """
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    MyRenamedClassInFilePerRootPersistence
                      f
                      modifiedRootNodeOnMainBranch
                    NewRootNodeInFilePerRootPersistence
                  my.solution.model_with_regular_xml_persistence
                    AddedRootToRegularPersistence
                my.solution.added.on.feature.branch.b.renamed
                  my.solution.added.on.feature.branch.b.renamed.a_model
                    NewRootNodeOnFeatureBranchB
                      abc
                      def
                test.org.modelix.git.import
            """.trimIndent(),
        )
    }
    fun `test result after merge`() = runWithImportResult { version ->
        assertCommit(
            version,
            "aac3b37b4c6213f8057961a79814989b2cbf3007",
            "Merge branch 'refs/heads/feature-A'",
            """
                my.devkit
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    AnotherRootNode
                      m1
                      m2
                      m3
                    MyClass
                      f
                      modifiedRootNodeOnMainBranch
                  my.solution.model_with_regular_xml_persistence
                test.org.modelix.git.import
            """.trimIndent(),
        )
    }
    fun `test result of feature branch before merge`() = runWithImportResult { version ->
        assertCommit(
            version,
            "bcb8a4d9ca158e0c77cdb875f9594b154efdf061",
            "Feat(feature-A): Add utils model and enhance devkit",
            """
                my.devkit
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    AnotherRootNode
                      m1
                      m2
                      m3
                    MyClass
                      f
                  my.solution.model_with_regular_xml_persistence
                test.org.modelix.git.import
            """.trimIndent(),
        )
    }
    fun `test result of main branch before merge`() = runWithImportResult { version ->
        assertCommit(
            version,
            "6f73ef5a56b9dd6a8c6140f1369fbf93de4046e5",
            "refactor: changed binary peristence root node",
            """
                my.devkit
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    AnotherRootNode
                      m1
                      m2
                      m3
                    MyClass
                      f
                      modifiedRootNodeOnMainBranch
                test.org.modelix.git.import
            """.trimIndent(),
        )
    }
    fun `test result after converting from file_per_root to regular persistence`() = runWithImportResult { version ->
        assertCommit(
            version,
            "96a0ec86422c5be64d53907195b0dec5f278234e",
            "refactor: converted model from file_per_root to regular persistence",
            """
                my.devkit
                my.language
                  my.language.behavior
                  my.language.constraints
                  my.language.editor
                  my.language.generator
                    my.language.generator.templates@generator
                      main
                  my.language.structure
                  my.language.typesystem
                my.language.runtime
                  my.language.runtime
                my.language.sandbox
                  my.language.sandbox
                my.solution
                  my.solution.model_with_binary_persistence
                    MyBinaryClass
                      plus
                        a
                  my.solution.model_with_file_per_root_persistence
                    MyRenamedClassInFilePerRootPersistence
                      f
                      modifiedRootNodeOnMainBranch
                    NewRootNodeInFilePerRootPersistence
                  my.solution.model_with_regular_xml_persistence
                    AddedRootToRegularPersistence
                test.org.modelix.git.import
            """.trimIndent(),
        )
    }

    /**
     * For the import performance it's important that the diff is minimal.
     */
    fun `test diff doesn't return unnecessary objects`() = runWithImportResult { latestVersion ->
        for (version in latestVersion.historyAsSequence()) {
            for (parentVersion in version.getParentVersions()) {
                val baseObjects = parentVersion.getModelTree().asObject().getDescendantsAndSelf().map { it.getHash() }.toList()
                    .getBlocking(parentVersion.asObject().graph)
                val baseObjectsSet = baseObjects.toSet()

                // no object is returned twice
                assertEquals(baseObjects.toSet().size, baseObjects.size)

                val filter = ObjectDeltaFilter(
                    knownVersions = setOf(parentVersion.getContentHash()),
                    includeHistory = false,
                    includeOperations = false,
                )
                val deltaObjects = version.diff(parentVersion, filter)
                    .map {
                        check(!baseObjectsSet.contains(it.getHash())) {
                            "Unnecessary: $it"
                        }
                        it
                    }
                    .toList()
                    .getBlocking(parentVersion.asObject().graph)

                // also the delta itself doesn't contain duplicate objects
                val duplicateObjects: List<Object<IObjectData>> = deltaObjects.groupBy { it.getHash() }.filter { it.value.size > 1 }.map { it.value.first() }
                if (duplicateObjects.isNotEmpty()) {
                    // place breakpoint here for debugging
                    version.diff(parentVersion, filter).toList().getBlocking(parentVersion.asObject().graph)
                }
                assertEquals(emptyList<Object<IObjectData>>(), duplicateObjects)

                // the delta doesn't contain any objects that are already part of the parent
                val unnecessaryObjects = deltaObjects.filter { baseObjectsSet.contains(it.getHash()) }
                if (unnecessaryObjects.isNotEmpty()) {
                    // just for debugging
                    parentVersion.getModelTree().asObject().getDescendantsAndSelf().toList()
                        .getBlocking(parentVersion.asObject().graph).joinToString("\n") { it.toString() }
                        .let { File("oldVersion.txt").writeText(it) }
                    version.getModelTree().asObject().getDescendantsAndSelf().toList()
                        .getBlocking(parentVersion.asObject().graph).joinToString("\n") { it.toString() }
                        .let { File("newVersion.txt").writeText(it) }
                    version.diff(parentVersion, filter).toList().getBlocking(parentVersion.asObject().graph)
                }
                assertEquals(emptyList<Object<IObjectData>>(), unnecessaryObjects)
            }
        }
    }

    fun `test incremental import branches`() = runWithModelServer { port ->
        val modelServerUrl = "http://localhost:$port"
        val repositoryId = RepositoryId("git-import-test")
        val targetBranch = repositoryId.getBranchReference("git-import")

        GitImporter(
            gitDir = extractTestProject().toFile(),
            modelServerUrl = modelServerUrl,
            baseBranch = repositoryId.getBranchReference(),
            targetBranchName = targetBranch.branchName,
            gitRevision = "bcb8a4d9ca158e0c77cdb875f9594b154efdf061",
        ).runSuspending()

        GitImporter(
            gitDir = extractTestProject().toFile(),
            modelServerUrl = modelServerUrl,
            baseBranch = targetBranch,
            targetBranchName = targetBranch.branchName,
            gitRevision = "6f73ef5a56b9dd6a8c6140f1369fbf93de4046e5",
        ).runSuspending()
    }

    fun `test incremental import simple`() = runWithModelServer { port ->
        val modelServerUrl = "http://localhost:$port"
        val repositoryId = RepositoryId("git-import-test")
        val targetBranch = repositoryId.getBranchReference("git-import")

        GitImporter(
            gitDir = extractTestProject().toFile(),
            modelServerUrl = modelServerUrl,
            baseBranch = repositoryId.getBranchReference(),
            targetBranchName = targetBranch.branchName,
            gitRevision = "5a2823ce182dc8419b7e06d2513407fcfd719c56",
        ).runSuspending()

        GitImporter(
            gitDir = extractTestProject().toFile(),
            modelServerUrl = modelServerUrl,
            baseBranch = targetBranch,
            targetBranchName = targetBranch.branchName,
            gitRevision = "389ccb51b6fb49656fcb6bca29235bdf8434fec7",
        ).runSuspending()
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

    fun runWithImportResult(body: (IVersion) -> Unit) = runWithModelServer { port ->

        val modelServerUrl = "http://localhost:$port"
        val repositoryId = RepositoryId("git-import-test")
        val targetBranchName = "git-import-result"
        val targetBranch = repositoryId.getBranchReference(targetBranchName)
        val importer = GitImporter(
            gitDir = extractTestProject().toFile(),
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
        body(latestVersion)
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
