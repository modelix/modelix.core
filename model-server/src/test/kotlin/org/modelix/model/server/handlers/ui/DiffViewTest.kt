package org.modelix.model.server.handlers.ui

import com.google.common.collect.ArrayListMultimap
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import org.junit.jupiter.api.Nested
import org.modelix.model.ModelFacade
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPNodeRef
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.installDefaultServerPlugins
import kotlin.test.BeforeTest
import kotlin.test.Test

class DiffViewTest {

    @Nested
    inner class DiffCalculationTest {
        @Test
        fun `added nodes are tracked correctly`() {
            val role = "testRole"
            val childIds = listOf(1234L, 5678L)
            val v1 = createCLVersion { tree -> tree }
            val v2 = createCLVersion(v1) { tree ->
                var newTree = tree.addNewChild(ITree.ROOT_ID, role, 0, childIds[0], null as IConcept?)
                newTree = newTree.addNewChild(ITree.ROOT_ID, role, 1, childIds[1], null as IConcept?)
                newTree
            }

            val root = v2.getTree().root
            val diff = requireNotNull(calculateDiff(v1, v2))

            diff.nodeAdditions.size() shouldBe 2
            root shouldBeIn diff.nodeAdditions.keySet()
            diff.nodeAdditions[root].map { it.id } shouldContainExactlyInAnyOrder childIds
        }

        @Test
        fun `removed nodes are tracked correctly`() {
            val role = "testRole"
            val childIds = listOf(1234L, 5678L)
            val v1 = createCLVersion { tree ->
                var newTree = tree.addNewChild(ITree.ROOT_ID, role, 0, childIds[0], null as IConcept?)
                newTree = newTree.addNewChild(ITree.ROOT_ID, role, 1, childIds[1], null as IConcept?)
                newTree
            }
            val v2 = createCLVersion(v1) { tree ->
                var newTree = tree.deleteNode(childIds[0])
                newTree = newTree.deleteNode(childIds[1])
                newTree
            }

            val root = v1.getTree().root

            val diff = requireNotNull(calculateDiff(v1, v2))

            diff.nodeRemovals.size() shouldBe 2
            root shouldBeIn diff.nodeRemovals.keySet()
            diff.nodeRemovals[root].map { it.id } shouldContainExactlyInAnyOrder childIds
        }

        @Test
        fun `property changes are tracked correctly`() {
            val propertyChanges = listOf(
                PropertyChange("propertyA", null, "valueA"),
                PropertyChange("propertyB", "oldB", "newB"),
            )
            val existingProperty = propertyChanges[1]
            val v1 = createCLVersion { tree ->
                val newTree = tree.setProperty(ITree.ROOT_ID, existingProperty.propertyRole, existingProperty.oldValue)
                newTree
            }
            val v2 = createCLVersion(v1) { tree ->
                var newTree = tree
                for (change in propertyChanges) {
                    newTree = newTree.setProperty(ITree.ROOT_ID, change.propertyRole, change.newValue)
                }
                newTree
            }

            val root = v2.getTree().root
            val diff = requireNotNull(calculateDiff(v1, v2))

            diff.propertyChanges.size() shouldBe 2
            root shouldBeIn diff.propertyChanges.keySet()
            diff.propertyChanges[root] shouldContainExactlyInAnyOrder propertyChanges
        }

        @Test
        fun `reference changes are tracked correctly`() {
            val node1Id: Long = 1234
            val node2Id: Long = 5678
            val refA = "refA"
            val refB = "refB"
            val v1 = createCLVersion { tree ->
                var newTree = tree.addNewChild(ITree.ROOT_ID, "testRole", 0, node1Id, null as IConcept?)
                newTree = newTree.addNewChild(ITree.ROOT_ID, "testRole", 1, node2Id, null as IConcept?)
                newTree = newTree.setReferenceTarget(node1Id, refA, ITree.ROOT_ID)
                newTree = newTree.setReferenceTarget(node1Id, refB, ITree.ROOT_ID)
                newTree
            }

            val refC = "refC"
            val v2 = createCLVersion(v1) { tree ->
                var newTree = tree.setReferenceTarget(node1Id, refA, null as INodeReference?)
                newTree = newTree.setReferenceTarget(node1Id, refB, node2Id)
                newTree = newTree.setReferenceTarget(node2Id, refC, node1Id)
                newTree
            }

            val expectedReferenceChangesForNode1 = setOf(
                ReferenceChange(
                    referenceRole = refA,
                    oldTarget = v1.getTree().root,
                    oldTargetRef = CPNodeRef.local(ITree.ROOT_ID),
                    newTarget = null,
                    newTargetRef = null,
                ),
                ReferenceChange(
                    referenceRole = refB,
                    oldTarget = v1.getTree().root,
                    oldTargetRef = CPNodeRef.local(ITree.ROOT_ID),
                    newTarget = v2.getTree().resolveElementSynchronous(node2Id),
                    newTargetRef = CPNodeRef.local(node2Id),
                ),
            )

            val expectedReferenceChangesForNode2 = setOf(
                ReferenceChange(
                    referenceRole = refC,
                    oldTarget = null,
                    oldTargetRef = null,
                    newTarget = v2.getTree().resolveElementSynchronous(node1Id),
                    newTargetRef = CPNodeRef.local(node1Id),
                ),
            )

            val diff = requireNotNull(calculateDiff(v1, v2))
            val node1 = v2.getTree().resolveElementSynchronous(node1Id)
            val node2 = v2.getTree().resolveElementSynchronous(node2Id)

            diff.referenceChanges.size() shouldBe 3
            diff.referenceChanges.keySet().shouldContainOnly(node1, node2)
            diff.referenceChanges[node1] shouldContainExactlyInAnyOrder expectedReferenceChangesForNode1
            diff.referenceChanges[node2] shouldContainExactlyInAnyOrder expectedReferenceChangesForNode2
        }

        @Test
        fun `children changes are tracked correctly`() {
            val childRemovalRole = "childRemoval"
            val childMoveFromRole = "childMoveFrom"
            val childMoveToRole = "childMoveTo"
            val v1 = createCLVersion { tree ->
                var newTree = tree.addNewChild(ITree.ROOT_ID, "testRole", 0, 10, null as IConcept?)
                newTree = newTree.addNewChild(10, childRemovalRole, 0, 101, null as IConcept?)
                newTree = newTree.addNewChild(10, childMoveFromRole, 0, 111, null as IConcept?)
                newTree = newTree.addNewChild(10, childMoveToRole, 0, 112, null as IConcept?)
                newTree
            }

            val childAdditionRole = "childAddition"
            val v2 = createCLVersion(v1) { tree ->
                var newTree = tree.addNewChild(10, childAdditionRole, 0, 120, null as IConcept?)
                newTree = newTree.deleteNode(101)
                newTree = newTree.moveChild(10, childMoveToRole, 0, 111)
                newTree
            }

            val expectedChildrenChange = ChildrenChange(
                oldParent = v1.getTree().resolveElementSynchronous(10),
                newParent = v2.getTree().resolveElementSynchronous(10),
                roles = mutableSetOf(childRemovalRole, childAdditionRole, childMoveFromRole, childMoveToRole),
            )

            val diff = requireNotNull(calculateDiff(v1, v2))

            diff.childrenChanges.size shouldBe 1
            10L shouldBeIn diff.childrenChanges.keys
            diff.childrenChanges[10] shouldBe expectedChildrenChange
        }

        @Test
        fun `containment changes are tracked correctly`() {
            val v1 = createCLVersion { tree ->
                var newTree = tree.addNewChild(ITree.ROOT_ID, "testRole", 0, 10, null as IConcept?)
                newTree = newTree.addNewChild(10, "preRoleChange", 0, 101, null as IConcept?)
                newTree = newTree.addNewChild(10, "moveAcrossParents", 0, 102, null as IConcept?)
                newTree
            }

            val v2 = createCLVersion(v1) { tree ->
                var newTree = tree.moveChild(ITree.ROOT_ID, "moveAcrossParents", 0, 102)
                newTree = newTree.moveChild(10, "postRoleChange", 0, 101)
                newTree
            }

            val diff = requireNotNull(calculateDiff(v1, v2))

            val expectedContainmentChanges = setOf(
                ContainmentChange(
                    node = v2.getTree().resolveElementSynchronous(101),
                    oldParent = v1.getTree().resolveElementSynchronous(10),
                    oldRole = "preRoleChange",
                    newParent = v2.getTree().resolveElementSynchronous(10),
                    newRole = "postRoleChange",
                ),
                ContainmentChange(
                    node = v2.getTree().resolveElementSynchronous(102),
                    oldParent = v1.getTree().resolveElementSynchronous(10),
                    oldRole = "moveAcrossParents",
                    newParent = v2.getTree().root!!,
                    newRole = "moveAcrossParents",
                ),
            )

            diff.containmentChanges shouldContainExactlyInAnyOrder expectedContainmentChanges
        }

        @Test
        fun `concept changes are tracked correctly`() {
            val v1 = createCLVersion { tree ->
                var newTree = tree.addNewChild(ITree.ROOT_ID, null, 0, 1234, BuiltinLanguages.MPSRepositoryConcepts.Module)
                newTree = newTree.addNewChild(1234, null, 0, 5678, BuiltinLanguages.MPSRepositoryConcepts.Model)
                newTree
            }

            val v2 = createCLVersion(v1) { tree ->
                var newTree = tree.setConcept(ITree.ROOT_ID, BuiltinLanguages.MPSRepositoryConcepts.Repository.getReference())
                newTree = newTree.setConcept(1234, BuiltinLanguages.MPSRepositoryConcepts.Project.getReference())
                newTree = newTree.setConcept(5678, null as IConceptReference?)
                newTree
            }

            val diff = requireNotNull(calculateDiff(v1, v2))

            val expectedConceptChanges = setOf(
                ConceptChange(
                    node = v2.getTree().root!!,
                    oldConcept = null,
                    newConcept = BuiltinLanguages.MPSRepositoryConcepts.Repository.getUID(),
                ),
                ConceptChange(
                    node = v2.getTree().resolveElementSynchronous(1234),
                    oldConcept = BuiltinLanguages.MPSRepositoryConcepts.Module.getUID(),
                    newConcept = BuiltinLanguages.MPSRepositoryConcepts.Project.getUID(),
                ),
                ConceptChange(
                    node = v2.getTree().resolveElementSynchronous(5678),
                    oldConcept = BuiltinLanguages.MPSRepositoryConcepts.Model.getUID(),
                    newConcept = null,
                ),
            )

            diff.conceptChanges shouldContainExactlyInAnyOrder expectedConceptChanges
        }

        @Test
        fun `null is returned when sizeLimit is exceeded`() {
            val v1 = createCLVersion { it }
            val v2 = createCLVersion(v1) { tree ->
                var newTree = tree.setProperty(ITree.ROOT_ID, "a", "123")
                newTree = newTree.setProperty(ITree.ROOT_ID, "b", "456")
                newTree
            }

            val limitExceeded = calculateDiff(v1, v2, 1)
            val atLimit = calculateDiff(v1, v2, 2)

            limitExceeded shouldBe null
            atLimit shouldNotBe null
        }
    }

    val repositoriesManager = mockk<RepositoriesManager>()
    val v1 = createCLVersion { it }
    val v2 = createCLVersion(v1) { it }

    private fun runDiffViewTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installDefaultServerPlugins()
            DiffView(repositoriesManager).init(this)
        }

        coEvery { repositoriesManager.getVersion(RepositoryId("test-repo"), "v1") } returns v1
        coEvery { repositoriesManager.getVersion(RepositoryId("test-repo"), "v2") } returns v2
        block()
    }

    @BeforeTest
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `all sections are present`() = runDiffViewTest {
        val expectedSections = setOf(
            "nodeAdditions",
            "nodeRemovals",
            "propertyChanges",
            "referenceChanges",
            "childrenChanges",
            "containmentChanges",
            "conceptChanges",
        )

        val response = client.getDiffView()

        val body: Element = Jsoup.parse(response.bodyAsText()).body()

        val divIds = body.getElementsByTag("div").mapNotNull { element ->
            element.id().takeIf { it.isNotBlank() }
        }

        response shouldHaveStatus HttpStatusCode.OK
        divIds shouldContainAll expectedSections
    }

    @Test
    fun `node additions are rendered`() = runDiffViewTest {
        mockkStatic(::calculateDiff)

        val mockedNodeAdditions = ArrayListMultimap.create<CPNode, CPNode>().apply {
            put(testParent, testChild)
        }

        every { calculateDiff(v1, v2, DiffView.DEFAULT_SIZE_LIMIT) } returns VersionDiff(
            nodeAdditions = mockedNodeAdditions,
            nodeRemovals = ArrayListMultimap.create(),
            propertyChanges = ArrayListMultimap.create(),
            referenceChanges = ArrayListMultimap.create(),
            childrenChanges = emptyMap(),
            containmentChanges = emptyList(),
            conceptChanges = emptyList(),
        )

        val response = client.getDiffView()
        val div = response.getDiffSection("nodeAdditions")

        response shouldHaveStatus HttpStatusCode.OK
        div.allElements.shouldContainTestParent()
        div.allElements.shouldContainTestChild()
    }

    @Test
    fun `node removals are rendered`() = runDiffViewTest {
        mockkStatic(::calculateDiff)

        val mockedNodeRemovals = ArrayListMultimap.create<CPNode, CPNode>().apply {
            put(testParent, testChild)
        }

        every { calculateDiff(v1, v2, DiffView.DEFAULT_SIZE_LIMIT) } returns VersionDiff(
            nodeAdditions = ArrayListMultimap.create(),
            nodeRemovals = mockedNodeRemovals,
            propertyChanges = ArrayListMultimap.create(),
            referenceChanges = ArrayListMultimap.create(),
            childrenChanges = emptyMap(),
            containmentChanges = emptyList(),
            conceptChanges = emptyList(),
        )

        val response = client.getDiffView()
        val div = response.getDiffSection("nodeRemovals")

        response shouldHaveStatus HttpStatusCode.OK
        div.allElements.shouldContainTestParent()
        div.allElements.shouldContainTestChild()
    }

    @Test
    fun `property changes are rendered`() = runDiffViewTest {
        mockkStatic(::calculateDiff)

        val mockedPropertyChanges = ArrayListMultimap.create<CPNode, PropertyChange>().apply {
            put(
                testParent,
                PropertyChange(
                    propertyRole = "testRole",
                    oldValue = "someValue",
                    newValue = null,
                ),
            )
        }

        every { calculateDiff(v1, v2, DiffView.DEFAULT_SIZE_LIMIT) } returns VersionDiff(
            nodeAdditions = ArrayListMultimap.create(),
            nodeRemovals = ArrayListMultimap.create(),
            propertyChanges = mockedPropertyChanges,
            referenceChanges = ArrayListMultimap.create(),
            childrenChanges = emptyMap(),
            containmentChanges = emptyList(),
            conceptChanges = emptyList(),
        )

        val response = client.getDiffView()
        val div = response.getDiffSection("propertyChanges")

        response shouldHaveStatus HttpStatusCode.OK
        div.allElements.shouldContainTestParent()
        div.allElements.map { it.ownText() }.apply {
            shouldContain("testRole")
            shouldContain("null")
        }
    }

    @Test
    fun `reference changes are rendered`() = runDiffViewTest {
        val parentRef = CPNodeRef.local(testParent.id)
        val childRef = CPNodeRef.local(testChild.id)
        mockkStatic(::calculateDiff)
        val changedRole = "testRef"

        val mockedReferenceChanges = ArrayListMultimap.create<CPNode, ReferenceChange>().apply {
            put(
                testParent,
                ReferenceChange(
                    referenceRole = changedRole,
                    oldTarget = null,
                    oldTargetRef = parentRef,
                    newTarget = testChild,
                    newTargetRef = childRef,
                ),
            )
        }

        every { calculateDiff(v1, v2, DiffView.DEFAULT_SIZE_LIMIT) } returns VersionDiff(
            nodeAdditions = ArrayListMultimap.create(),
            nodeRemovals = ArrayListMultimap.create(),
            propertyChanges = ArrayListMultimap.create(),
            referenceChanges = mockedReferenceChanges,
            childrenChanges = emptyMap(),
            containmentChanges = emptyList(),
            conceptChanges = emptyList(),
        )

        val response = client.getDiffView()
        val div = response.getDiffSection("referenceChanges")

        response shouldHaveStatus HttpStatusCode.OK
        div.allElements.shouldContainTestChild()
        div.allElements.shouldContainTestParent()
        div.allElements.map { it.ownText() }.apply {
            shouldContain(changedRole)
            shouldContain(parentRef.toString())
        }
    }

    @Test
    fun `children changes are rendered`() = runDiffViewTest {
        mockkStatic(::calculateDiff)
        val changedRoles = setOf("changedRoleA", "changedRoleB")
        val mockedChildrenChange = mapOf(
            testParent.id to ChildrenChange(testParent, testParent, changedRoles.toMutableSet()),
        )

        every { calculateDiff(v1, v2, DiffView.DEFAULT_SIZE_LIMIT) } returns VersionDiff(
            nodeAdditions = ArrayListMultimap.create(),
            nodeRemovals = ArrayListMultimap.create(),
            propertyChanges = ArrayListMultimap.create(),
            referenceChanges = ArrayListMultimap.create(),
            childrenChanges = mockedChildrenChange,
            containmentChanges = emptyList(),
            conceptChanges = emptyList(),
        )

        val response = client.getDiffView()
        val div = response.getDiffSection("childrenChanges")

        response shouldHaveStatus HttpStatusCode.OK
        div.allElements.shouldContainTestParent()
        div.allElements.map { it.ownText() } shouldContain changedRoles.joinToString(" ")
    }

    @Test
    fun `containment changes are rendered`() = runDiffViewTest {
        mockkStatic(::calculateDiff)
        val oldRole = "oldRole"
        val newRole = "newRole"

        val mockedContainmentChanges = listOf(
            ContainmentChange(
                node = testChild,
                oldParent = testParent,
                oldRole = oldRole,
                newParent = testParent,
                newRole = newRole,
            ),
        )

        every { calculateDiff(v1, v2, DiffView.DEFAULT_SIZE_LIMIT) } returns VersionDiff(
            nodeAdditions = ArrayListMultimap.create(),
            nodeRemovals = ArrayListMultimap.create(),
            propertyChanges = ArrayListMultimap.create(),
            referenceChanges = ArrayListMultimap.create(),
            childrenChanges = emptyMap(),
            containmentChanges = mockedContainmentChanges,
            conceptChanges = emptyList(),
        )

        val response = client.getDiffView()
        val div = response.getDiffSection("containmentChanges")

        response shouldHaveStatus HttpStatusCode.OK
        div.allElements.shouldContainTestChild()
        div.allElements.shouldContainTestParent()
        div.allElements.map { it.ownText() }.apply {
            shouldContain(oldRole)
            shouldContain(newRole)
        }
    }

    @Test
    fun `concept changes are rendered`() = runDiffViewTest {
        mockkStatic(::calculateDiff)
        val oldConcept = BuiltinLanguages.MPSRepositoryConcepts.Module.getUID()

        val mockedConceptChanges = listOf(
            ConceptChange(
                node = testParent,
                oldConcept = oldConcept,
                newConcept = null,
            ),
        )

        every { calculateDiff(v1, v2, DiffView.DEFAULT_SIZE_LIMIT) } returns VersionDiff(
            nodeAdditions = ArrayListMultimap.create(),
            nodeRemovals = ArrayListMultimap.create(),
            propertyChanges = ArrayListMultimap.create(),
            referenceChanges = ArrayListMultimap.create(),
            childrenChanges = emptyMap(),
            containmentChanges = emptyList(),
            conceptChanges = mockedConceptChanges,
        )

        val response = client.getDiffView()
        val div = response.getDiffSection("conceptChanges")

        response shouldHaveStatus HttpStatusCode.OK
        div.allElements.shouldContainTestParent()
        div.allElements.map { it.ownText() }.apply {
            shouldContain(oldConcept)
            shouldContain("null")
        }
    }

    @Test
    fun `invalid sizeLimit leads to BadRequest response`() = runDiffViewTest {
        val response = client.get("/diff/view") {
            parameter("repository", "test-repo")
            parameter("oldVersionHash", "v1")
            parameter("newVersionHash", "v2")
            parameter("sizeLimit", "asdf")
        }
        response shouldHaveStatus HttpStatusCode.BadRequest
        response.bodyAsText() shouldContain "invalid sizeLimit"
    }

    @Test
    fun `warning is shown when sizeLimit is exceeded`() = runDiffViewTest {
        mockkStatic(::calculateDiff)

        every { calculateDiff(v1, v2, 500) } returns null

        val response = client.get("/diff/view") {
            parameter("repository", "test-repo")
            parameter("oldVersionHash", "v1")
            parameter("newVersionHash", "v2")
            parameter("sizeLimit", "500")
        }

        response shouldHaveStatus HttpStatusCode.OK
        response.bodyAsText() shouldContain "WARNING"
    }
}

private suspend fun HttpClient.getDiffView() = get("/diff/view") {
    parameter("repository", "test-repo")
    parameter("oldVersionHash", "v1")
    parameter("newVersionHash", "v2")
}

private suspend fun HttpResponse.getDiffSection(id: String): Element {
    val body: Element = Jsoup.parse(this.bodyAsText()).body()
    return requireNotNull(body.getElementById(id)) { "diff section not found" }
}

private fun createCLVersion(baseVersion: CLVersion? = null, treeModification: (ITree) -> ITree): CLVersion {
    val baseTree = baseVersion?.getTree() ?: ModelFacade.newLocalTree()
    val tree = treeModification(baseTree) as CLTree

    return CLVersion.createRegularVersion(
        id = IdGenerator.getInstance(1).generate(),
        author = "diffview-test",
        tree = tree,
        baseVersion = baseVersion,
        operations = emptyArray(),
    )
}

private val testParent = CPNode.create(
    id = 1234,
    concept = null,
    parentId = ITree.ROOT_ID,
    roleInParent = null,
    childrenIds = LongArray(0),
    propertyRoles = arrayOf(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getUID()),
    propertyValues = arrayOf("MyParent"),
    referenceRoles = emptyArray(),
    referenceTargets = emptyArray(),
)

private val testChild = CPNode.create(
    id = 5678,
    concept = null,
    parentId = 1234,
    roleInParent = null,
    childrenIds = LongArray(0),
    propertyRoles = arrayOf(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getUID()),
    propertyValues = arrayOf("MyChild"),
    referenceRoles = emptyArray(),
    referenceTargets = emptyArray(),
)

private fun Elements.shouldContainTestParent() {
    any { it.ownText().contains(1234L.toString()) && it.ownText().contains("MyParent") } shouldBe true
}

private fun Elements.shouldContainTestChild() {
    any { it.ownText().contains(5678L.toString()) && it.ownText().contains("MyChild") } shouldBe true
}
