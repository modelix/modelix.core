package org.modelix.model.server.handlers.ui

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLPath
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.html.respondHtmlTemplate
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.html.FlowContent
import kotlinx.html.TABLE
import kotlinx.html.a
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.thead
import kotlinx.html.tr
import org.modelix.authorization.checkPermission
import org.modelix.authorization.hasPermission
import org.modelix.authorization.requiresLogin
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPNodeRef
import org.modelix.model.server.ModelServerPermissionSchema
import org.modelix.model.server.handlers.HttpException
import org.modelix.model.server.handlers.RepositoriesManager
import org.modelix.model.server.handlers.VersionNotFoundException
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.templates.PageWithMenuBar

/**
 * Handler which enables to view tree diffs between two [CLVersion] instances of a repository.
 */
@Suppress("TooManyFunctions")
class DiffView(private val repositoryManager: RepositoriesManager) {

    private val baseUrl = "../.."

    @Suppress("UndocumentedPublicClass")
    companion object {
        /**
         * The default maximum number of displayable changes.
         */
        const val DEFAULT_SIZE_LIMIT = 50000
    }

    /**
     * Initializes this handler in the given [application]
     */
    fun init(application: Application) {
        application.routing {
            requiresLogin {
                get("/diff") {
                    @OptIn(RequiresTransaction::class)
                    call.respondHtmlTemplateInTransaction(repositoryManager.getTransactionManager(), PageWithMenuBar("diff", "..")) {
                        val visibleRepositories = repositoryManager.getRepositories().filter {
                            call.hasPermission(ModelServerPermissionSchema.repository(it).list)
                        }
                        bodyContent {
                            buildDiffInputPage(visibleRepositories)
                        }
                    }
                }
                get("/diff/view") {
                    val repoId =
                        (call.request.queryParameters["repository"])?.let { param -> RepositoryId(param) } ?: throw HttpException(
                            HttpStatusCode.BadRequest,
                            "missing repository",
                        )
                    call.checkPermission(ModelServerPermissionSchema.repository(repoId).objects.read)

                    val oldVersionHash = call.request.queryParameters["oldVersionHash"] ?: throw HttpException(
                        HttpStatusCode.BadRequest,
                        "missing oldVersionHash",
                    )
                    val newVersionHash = call.request.queryParameters["newVersionHash"] ?: throw HttpException(
                        HttpStatusCode.BadRequest,
                        "missing newVersionHash",
                    )

                    val sizeLimit = call.request.queryParameters["sizeLimit"]?.let { param ->
                        param.toIntOrNull() ?: throw HttpException(HttpStatusCode.BadRequest, "invalid sizeLimit")
                    } ?: DEFAULT_SIZE_LIMIT

                    val oldVersion = repositoryManager.getVersion(repoId, oldVersionHash) ?: throw VersionNotFoundException(
                        oldVersionHash,
                    )
                    val newVersion = repositoryManager.getVersion(repoId, newVersionHash) ?: throw VersionNotFoundException(
                        newVersionHash,
                    )

                    val diff = calculateDiff(oldVersion, newVersion, sizeLimit)
                    call.respondHtmlTemplate(PageWithMenuBar("diff", baseUrl)) {
                        bodyContent {
                            buildDiffView(diff, oldVersionHash, newVersionHash, repoId.id, sizeLimit)
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.buildDiffInputPage(visibleRepositories: List<RepositoryId>) {
        h1 { +"Diff View" }
        div {
            if (visibleRepositories.isEmpty()) {
                +"No repositories available."
                return@div
            }
            form("/diff/view") {
                label {
                    htmlFor = "repository"
                    +"repository:"
                }
                select {
                    name = "repository"
                    id = "repository"
                    for (repositoryId in visibleRepositories) {
                        option(content = repositoryId.id)
                    }
                }
                br()
                br()
                label {
                    htmlFor = "oldVersionHash"
                    +"oldVersionHash:"
                }
                textInput(name = "oldVersionHash") { id = "oldVersionHash" }
                br()
                br()
                label {
                    htmlFor = "newVersionHash"
                    +"newVersionHash:"
                }
                textInput(name = "newVersionHash") { id = "newVersionHash" }
                br()
                br()
                submitInput()
            }
        }
    }

    private fun FlowContent.buildDiffView(
        diff: VersionDiff?,
        oldVersionHash: String,
        newVersionHash: String,
        repositoryId: String,
        sizeLimit: Int,
    ) {
        h1 { +"Version Diff" }
        p {
            +"Old Version: "
            a(constructUrlToContentExplorer(repositoryId, oldVersionHash)) { +oldVersionHash }
        }
        p {
            +"New Version:"
            a(constructUrlToContentExplorer(repositoryId, newVersionHash)) { +newVersionHash }
        }

        if (diff == null) {
            p {
                +"""
                WARNING: The diff exceeds the current sizeLimit of $sizeLimit changes.
                Increasing the sizeLimit could lead to browser instability.
                You can specify a new sizeLimit via the GET query parameter e.g. '&sizeLimit=100000'.
                """.trimIndent()
            }
            return
        }

        buildNewNodesSection(diff, repositoryId, newVersionHash)

        buildRemovedNodesSection(diff, repositoryId, oldVersionHash)

        buildPropertyChangesSection(diff, repositoryId, newVersionHash)

        buildReferenceChangesSection(diff, repositoryId, newVersionHash, oldVersionHash)

        buildChildrenChangesSection(diff, repositoryId, newVersionHash, oldVersionHash)

        buildContainmentChangesSection(diff, repositoryId, newVersionHash, oldVersionHash)

        buildConceptChangesSection(diff, repositoryId, newVersionHash)
    }

    private fun FlowContent.buildNewNodesSection(
        diff: VersionDiff,
        repositoryId: String,
        newVersionHash: String,
    ) {
        div {
            id = "nodeAdditions"
            if (diff.nodeAdditions.isEmpty) {
                p { +"No new nodes." }
                return@div
            }
            h2 { +"New Nodes" }
            table {
                createTableHead(listOf("Parent", "Role", "New Node"))
                createAdditionOrRemovalTableBody(repositoryId, newVersionHash, diff.nodeAdditions)
            }
        }
    }

    private fun FlowContent.buildRemovedNodesSection(diff: VersionDiff, repositoryId: String, oldVersionHash: String) {
        div {
            id = "nodeRemovals"
            if (diff.nodeRemovals.isEmpty) {
                p { +"No removed nodes." }
                return@div
            }
            h2 { +"Removed Nodes" }
            table {
                createTableHead(listOf("Parent", "Role", "Removed Node"))
                createAdditionOrRemovalTableBody(repositoryId, oldVersionHash, diff.nodeRemovals)
            }
        }
    }

    private fun TABLE.createAdditionOrRemovalTableBody(
        repositoryId: String,
        versionHashForLinks: String,
        nodeAdditionsOrRemovals: Multimap<CPNode, CPNode>,
    ) {
        tbody {
            for (parent in nodeAdditionsOrRemovals.keySet().sortedBy { it.id }) {
                val size = nodeAdditionsOrRemovals[parent].size
                tr {
                    td {
                        style = "background-color: white;"
                        rowSpan = size.plus(1).toString()
                        createLinkToContentExplorer(repositoryId, versionHashForLinks, parent)
                    }
                }
                for (child in nodeAdditionsOrRemovals[parent]) {
                    tr {
                        td { +"${child.roleInParent}" }
                        td {
                            createLinkToContentExplorer(repositoryId, versionHashForLinks, child)
                        }
                    }
                }
                tr { style = "display:none;" } // workaround for row color
            }
        }
    }

    private fun FlowContent.buildPropertyChangesSection(
        diff: VersionDiff,
        repositoryId: String,
        versionHashForLinks: String,
    ) {
        div {
            id = "propertyChanges"
            if (diff.propertyChanges.isEmpty) {
                p { +"No property changes." }
                return@div
            }
            h2 { +"Property Changes" }
            table {
                createTableHead(listOf("Node", "Role", "Old Value", "New Value"))
                tbody {
                    for (node in diff.propertyChanges.keySet().sortedBy { it.id }) {
                        val size = diff.propertyChanges[node].size
                        tr {
                            td {
                                style = "background-color: white;"
                                rowSpan = size.plus(1).toString()
                                createLinkToContentExplorer(repositoryId, versionHashForLinks, node)
                            }
                        }
                        for (change in diff.propertyChanges[node]) {
                            tr {
                                td { +change.propertyRole }
                                td { +"${change.oldValue}" }
                                td { +"${change.newValue}" }
                            }
                        }
                        tr { style = "display:none;" } // workaround for row color
                    }
                }
            }
        }
    }

    private fun FlowContent.buildReferenceChangesSection(
        diff: VersionDiff,
        repositoryId: String,
        newVersionHash: String,
        oldVersionHash: String,
    ) {
        div {
            id = "referenceChanges"
            if (diff.referenceChanges.isEmpty) {
                p { +"No reference changes." }
                return@div
            }
            h2 { +"Reference Changes" }
            table {
                createTableHead(listOf("Node", "Role", "Old Target", "New Target"))
                tbody {
                    for (node in diff.referenceChanges.keySet().sortedBy { it.id }) {
                        val size = diff.referenceChanges[node].size
                        tr {
                            td {
                                style = "background-color: white;"
                                rowSpan = size.plus(1).toString()
                                createLinkToContentExplorer(repositoryId, newVersionHash, node)
                            }
                        }

                        for (change in diff.referenceChanges[node]) {
                            tr {
                                td { +change.referenceRole }
                                td {
                                    if (change.oldTarget != null) {
                                        createLinkToContentExplorer(repositoryId, oldVersionHash, change.oldTarget)
                                        return@td
                                    }
                                    +"${change.oldTargetRef}"
                                }
                                td {
                                    if (change.newTarget != null) {
                                        createLinkToContentExplorer(repositoryId, newVersionHash, change.newTarget)
                                    }
                                    +"${change.newTargetRef}"
                                }
                            }
                        }

                        tr { style = "display:none;" } // workaround for row color
                    }
                }
            }
        }
    }

    private fun FlowContent.buildChildrenChangesSection(
        diff: VersionDiff,
        repositoryId: String,
        newVersionHash: String,
        oldVersionHash: String,
    ) {
        div {
            id = "childrenChanges"
            if (diff.childrenChanges.isEmpty()) {
                p { +"No children changes." }
                return@div
            }
            h2 { +"Children Changes" }
            table {
                createTableHead(listOf("Parent in old version", "Parent in new version", "Changed Roles"))
                tbody {
                    for ((_, change) in diff.childrenChanges.entries.sortedBy { it.key }) {
                        tr {
                            td {
                                createLinkToContentExplorer(repositoryId, oldVersionHash, change.oldParent)
                            }
                            td {
                                createLinkToContentExplorer(repositoryId, newVersionHash, change.newParent)
                            }
                            td {
                                +change.roles.joinToString("\n")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.buildContainmentChangesSection(
        diff: VersionDiff,
        repositoryId: String,
        newVersionHash: String,
        oldVersionHash: String,
    ) {
        div {
            id = "containmentChanges"
            if (diff.containmentChanges.isEmpty()) {
                p { +"No containment changes." }
                return@div
            }
            h2 { +"Containment Changes" }
            table {
                createTableHead(listOf("Node", "Old Parent", "Old Role", "New Parent", "New Role"))
                tbody {
                    for (change in diff.containmentChanges) {
                        tr {
                            td {
                                createLinkToContentExplorer(repositoryId, newVersionHash, change.node)
                            }
                            td {
                                createLinkToContentExplorer(repositoryId, oldVersionHash, change.oldParent)
                            }
                            td {
                                +"${change.oldRole}"
                            }
                            td {
                                createLinkToContentExplorer(repositoryId, newVersionHash, change.newParent)
                            }
                            td {
                                +"${change.newRole}"
                            }
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.buildConceptChangesSection(
        diff: VersionDiff,
        repositoryId: String,
        newVersionHash: String,
    ) {
        div {
            id = "conceptChanges"
            if (diff.conceptChanges.isEmpty()) {
                p { +"No concept changes." }
                return@div
            }
            h2 { +"Concept Changes" }
            table {
                createTableHead(listOf("Node", "Old Concept", "New Concept"))
                tbody {
                    for (change in diff.conceptChanges) {
                        tr {
                            td {
                                createLinkToContentExplorer(repositoryId, newVersionHash, change.node)
                            }
                            td { +"${change.oldConcept}" }
                            td { +"${change.newConcept}" }
                        }
                    }
                }
            }
        }
    }

    private fun TABLE.createTableHead(headings: List<String>) {
        thead {
            tr {
                for (heading in headings) {
                    td { +heading }
                }
            }
        }
    }

    private fun FlowContent.createLinkToContentExplorer(
        repositoryId: String,
        versionHash: String,
        node: CPNode,
    ) {
        a(constructUrlToContentExplorer(repositoryId, versionHash, node)) {
            +"${node.id}${node.getNameClarification()}"
        }
    }

    private fun constructUrlToContentExplorer(
        repositoryId: String,
        versionHash: String,
        node: CPNode? = null,
    ): String {
        val url = "$baseUrl/content/repositories/${repositoryId.encodeURLPath()}/versions/${versionHash.encodeURLPath()}/"
        return if (node == null) {
            url
        } else {
            url + "?expandTo=${node.id}"
        }
    }
}

private fun CPNode.getNameClarification(): String {
    val name = getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name.getUID())
        ?: getPropertyValue("name")

    return name?.let { "($it)" }.orEmpty()
}

private fun CLTree.resolveOrThrow(nodeId: Long): CPNode =
    requireNotNull(resolveElementSynchronous(nodeId)) { "node not found. id = $nodeId" }

@Suppress("TooGenericExceptionCaught", "SwallowedException") // the exception is also thrown generically and we don't need the original exception
private fun CLTree.tryResolve(ref: CPNodeRef?): CPNode? {
    return try {
        ref?.takeIf { it.isLocal }?.elementId?.let { resolveElementSynchronous(it) }
    } catch (e: RuntimeException) {
        null
    }
}

private class SizeLimitExceededException(sizeLimit: Int) : RuntimeException("The sizeLimit was exceeded. [sizeLimit] = $sizeLimit")

/**
 * @return null if the [sizeLimit] of changes was exceeded
 */
internal fun calculateDiff(oldVersion: CLVersion, newVersion: CLVersion, sizeLimit: Int = Int.MAX_VALUE): VersionDiff? {
    val oldTree = oldVersion.getTree()
    val newTree = newVersion.getTree()

    val nodeAdditions = ArrayListMultimap.create<CPNode, CPNode>()
    val nodeRemovals = ArrayListMultimap.create<CPNode, CPNode>()
    val propertyChanges = ArrayListMultimap.create<CPNode, PropertyChange>()
    val referenceChanges = ArrayListMultimap.create<CPNode, ReferenceChange>()
    val childrenChanges = mutableMapOf<Long, ChildrenChange>()
    val containmentChanges = mutableListOf<ContainmentChange>()
    val conceptChanges = mutableListOf<ConceptChange>()

    var changeCounter = 0L

    fun recordChangeIfLimitNotReached(body: () -> Unit) {
        if (changeCounter >= sizeLimit) {
            throw SizeLimitExceededException(sizeLimit)
        }
        body()
        changeCounter++
    }

    @Suppress("SwallowedException") // we don't need the exception
    try {
        // TODO re-implement using streams
        newTree.visitChanges(
            oldTree,
            object : ITreeChangeVisitorEx {
                override fun nodeRemoved(nodeId: Long) = recordChangeIfLimitNotReached {
                    val node = oldTree.resolveOrThrow(nodeId)
                    val parent = oldTree.resolveOrThrow(node.parentId)
                    nodeRemovals.put(parent, node)
                }

                override fun nodeAdded(nodeId: Long) = recordChangeIfLimitNotReached {
                    val node = newTree.resolveOrThrow(nodeId)
                    val parent = newTree.resolveOrThrow(node.parentId)
                    nodeAdditions.put(parent, node)
                }

                override fun containmentChanged(nodeId: Long) = recordChangeIfLimitNotReached {
                    val oldNode = oldTree.resolveOrThrow(nodeId)
                    val newNode = newTree.resolveOrThrow(nodeId)

                    containmentChanges.add(
                        ContainmentChange(
                            node = newNode,
                            oldParent = oldTree.resolveOrThrow(oldNode.parentId),
                            oldRole = oldNode.roleInParent,
                            newParent = newTree.resolveOrThrow(newNode.parentId),
                            newRole = newNode.roleInParent,
                        ),
                    )
                }

                override fun conceptChanged(nodeId: Long) {
                    val node = newTree.resolveOrThrow(nodeId)
                    conceptChanges.add(
                        ConceptChange(
                            node = node,
                            oldConcept = oldTree.resolveOrThrow(nodeId).concept,
                            newConcept = node.concept,
                        ),
                    )
                }

                override fun childrenChanged(nodeId: Long, role: String?) = recordChangeIfLimitNotReached {
                    val existingChange = childrenChanges[nodeId]
                    if (existingChange == null) {
                        childrenChanges[nodeId] = ChildrenChange(
                            oldParent = oldTree.resolveOrThrow(nodeId),
                            newParent = newTree.resolveOrThrow(nodeId),
                            roles = mutableSetOf(role),
                        )
                    } else {
                        existingChange.roles.add(role)
                    }
                }

                override fun referenceChanged(nodeId: Long, role: String) = recordChangeIfLimitNotReached {
                    val oldNode = oldTree.resolveOrThrow(nodeId)
                    val oldTargetRef = oldNode.getReferenceTarget(role)
                    val oldTarget = oldTree.tryResolve(oldTargetRef)

                    val newNode = newTree.resolveOrThrow(nodeId)
                    val newTargetRef = newNode.getReferenceTarget(role)
                    val newTarget = newTree.tryResolve(newTargetRef)

                    referenceChanges.put(newNode, ReferenceChange(role, oldTarget, oldTargetRef, newTarget, newTargetRef))
                }

                override fun propertyChanged(nodeId: Long, role: String) = recordChangeIfLimitNotReached {
                    val oldNode = oldTree.resolveOrThrow(nodeId)
                    val oldValue = oldNode.getPropertyValue(role)

                    val newNode = newTree.resolveOrThrow(nodeId)
                    val newValue = newNode.getPropertyValue(role)

                    propertyChanges.put(newNode, PropertyChange(role, oldValue, newValue))
                }
            },
        )
    } catch (e: SizeLimitExceededException) {
        return null
    }

    return VersionDiff(
        nodeAdditions,
        nodeRemovals,
        propertyChanges,
        referenceChanges,
        childrenChanges,
        containmentChanges,
        conceptChanges,
    )
}

internal data class VersionDiff(
    /** new parent to new children */
    val nodeAdditions: Multimap<CPNode, CPNode>,
    /** old parent to node before removal */
    val nodeRemovals: Multimap<CPNode, CPNode>,
    /** node in new version to change */
    val propertyChanges: Multimap<CPNode, PropertyChange>,
    /** node in new version to change */
    val referenceChanges: Multimap<CPNode, ReferenceChange>,
    /** parent id to children change */
    val childrenChanges: Map<Long, ChildrenChange>,
    val containmentChanges: List<ContainmentChange>,
    val conceptChanges: List<ConceptChange>,
)

internal data class ContainmentChange(
    val node: CPNode,
    val oldParent: CPNode,
    val oldRole: String?,
    val newParent: CPNode,
    val newRole: String?,
)

internal data class ConceptChange(
    val node: CPNode,
    val oldConcept: String?,
    val newConcept: String?,
)

internal data class ChildrenChange(
    val oldParent: CPNode,
    val newParent: CPNode,
    val roles: MutableSet<String?>,
)

internal data class ReferenceChange(
    val referenceRole: String,
    val oldTarget: CPNode?,
    val oldTargetRef: CPNodeRef?,
    val newTarget: CPNode?,
    val newTargetRef: CPNodeRef?,
)

internal data class PropertyChange(
    val propertyRole: String,
    val oldValue: String?,
    val newValue: String?,
)
