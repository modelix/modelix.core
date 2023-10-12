/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.mps.sync.binding

import com.intellij.openapi.diagnostic.logger
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations
import jetbrains.mps.project.ModelImporter
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.mps.openapi.event.SNodeAddEvent
import org.jetbrains.mps.openapi.event.SNodeRemoveEvent
import org.jetbrains.mps.openapi.event.SReferenceChangeEvent
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.deepUnwrapNode
import org.modelix.model.api.resolveIn
import org.modelix.model.area.CompositeArea
import org.modelix.model.area.PArea
import org.modelix.model.lazy.IBulkTree
import org.modelix.model.lazy.PrefetchCache
import org.modelix.model.mpsadapters.MPSArea
import org.modelix.model.mpsadapters.MPSConcept
import org.modelix.model.mpsadapters.MPSNode
import org.modelix.model.mpsadapters.NodeAsMPSNode
import org.modelix.mps.sync.ICloudRepository
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.synchronization.Synchronizer
import org.modelix.mps.sync.util.index
import org.modelix.mps.sync.util.mapToMpsNode

// status: ready to test
class ModelSynchronizer(
    private val modelNodeId: Long,
    private val model: SModel,
    private val cloudRepository: ICloudRepository,
) {
    companion object {
        val USED_DEVKITS = "usedDevkits"
        val MPS_NODE_ID_PROPERTY_NAME = "#mpsNodeId#"
    }

    private val logger = logger<ModelSynchronizer>()

    private val pendingReferences: PendingReferences = PendingReferences()

    private val branch: IBranch
        get() = this.cloudRepository.getBranch()

    private val nodeMap = NodeMap { this.branch }

    private fun prefetchModelContent(tree: ITree?) {
        if (tree is IBulkTree) {
            tree.getDescendants(modelNodeId, true)
        }
    }

    fun runAndFlushReferences(runnable: Runnable) = pendingReferences.runAndFlush(runnable)

    fun syncModelToMPS(tree: ITree, withInitialRemoval: Boolean) {
        PrefetchCache.Companion.with(tree) {
            logger.trace("syncModel initialRemoval=$withInitialRemoval on model ${model.name.longName}")
            if (withInitialRemoval) {
                model.rootNodes.toList().forEach { root -> root.children.forEach { SNodeOperations.deleteNode(it) } }
            }
            pendingReferences.runAndFlush {
                prefetchModelContent(tree)
                syncRootNodesToMPS()
                syncModelPropertiesToMPS(tree)
            }
        }
    }

    fun syncModelPropertiesToMPS(tree: ITree) =
        ModelPropertiesSynchronizer.syncModelPropertiesToMPS(tree, model, modelNodeId, cloudRepository)

    fun fullSyncFromMPS() {
        val tree = branch.transaction.tree
        if (!tree.containsNode(modelNodeId)) {
            logger.warn("Skipping sync for $this, because the model node ${java.lang.Long.toHexString(modelNodeId)} doesn't exist in the cloud model")
            return
        }
        PrefetchCache.Companion.with(tree) {
            pendingReferences.runAndFlush {
                prefetchModelContent(tree)
                syncModelPropertiesFromMPS()
                syncRootNodesFromMPS()
            }
        }
        PArea(branch).executeWrite { }
    }

    private fun syncRootNodesFromMPS() {
        val transaction = branch.writeTransaction
        val syncedNodes = createChildrenSynchronizer(
            modelNodeId,
            BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.getSimpleName(),
        ).syncToCloud(transaction)
        syncedNodes.forEach {
            syncNodeFromMPS(it.value, true)
        }
    }

    private fun syncRootNodesToMPS() {
        val transaction = branch.transaction
        val syncedNodes = createChildrenSynchronizer(
            modelNodeId,
            BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.getSimpleName(),
        ).syncToMPS(transaction.tree)
        syncedNodes.forEach {
            syncNodeToMPS(it.key, transaction.tree, true)
        }
    }

    private fun syncModelPropertiesFromMPS() =
        ModelPropertiesSynchronizer(modelNodeId, model, cloudRepository).syncModelPropertiesFromMPS()

    fun syncUsedLanguagesAndDevKitsFromMPS() =
        ModelPropertiesSynchronizer(modelNodeId, model, cloudRepository).syncUsedLanguagesAndDevKitsFromMPS()

    fun syncModelImportsFromMPS() {
        ModelPropertiesSynchronizer(modelNodeId, model, cloudRepository).syncModelImportsFromMPS()
    }

    fun getOrCreateMPSNode(nodeId: Long, tree: ITree): SNode {
        check(nodeId != 0L && nodeId != ITree.ROOT_ID) { "Invalid ID $nodeId" }
        return nodeMap.getOrCreateNode(nodeId) {
            val concept = tree.getConcept(nodeId)
            check(concept != null) { "Node has no concept: $nodeId" }
            val sconcept = MPSConcept.unwrap(concept)
            check(sconcept != null) { "Node has no MPS concept: $nodeId, $concept" }
            sconcept
        }
    }

    fun syncNodeToMPS(nodeId: Long, tree: ITree, includeDescendants: Boolean) {
        logger.trace("syncNode nodeId: $nodeId")
        try {
            val concept = MPSConcept.unwrap(tree.getConcept(nodeId))
            check(concept != null) {
                "Node has no concept: ${java.lang.Long.toHexString(nodeId)}. Role: ${
                    tree.getRole(
                        nodeId,
                    )
                }, Concept: ${tree.getConcept(nodeId)}"
            }
            val node = nodeMap.getOrCreateNode(nodeId) { concept }
            concept.properties.forEach { property ->
                node.setProperty(property, tree.getProperty(nodeId, property.name))
            }

            concept.referenceLinks.forEach { link ->
                syncReferenceToMPS(nodeId, link.name, tree)
            }
        } catch (ex: Exception) {
            logger.error("Failed to snyc node $nodeId", ex)
        }

        syncChildrenToMPS(nodeId, tree, includeDescendants)
    }

    fun syncPropertyToMPS(nodeId: Long, role: String, tree: ITree) {
        val concept = MPSConcept.unwrap(tree.getConcept(nodeId))
        val mpsNode = getOrCreateMPSNode(nodeId, tree)
        val mpsProperty: SProperty = findProperty(concept!!, role)
        mpsNode.setProperty(mpsProperty, tree.getProperty(nodeId, role))
    }

    fun syncReferenceToMPS(nodeId: Long, role: String, tree: ITree): SNode? {
        pendingReferences.add {
            try {
                val node = getOrCreateMPSNode(nodeId, tree)
                val target = tree.getReferenceTarget(nodeId, role)

                val repo = model.repository
                val resolveContext = if (repo == null) {
                    // We look in NodeMap instead
                    CompositeArea(PArea(branch), nodeMap)
                } else {
                    CompositeArea(PArea(branch), MPSArea(repo), nodeMap)
                }
                var targetNode = target?.resolveIn(resolveContext)

                var targetSNode: SNode? = null
                var targetAsPNodeAdapter: PNodeAdapter? = null
                if (targetNode != null) {
                    targetNode = deepUnwrapNode(targetNode)
                }
                if (targetNode is PNodeAdapter) {
                    targetAsPNodeAdapter = targetNode
                }
                targetSNode = if (targetAsPNodeAdapter == null) {
                    NodeAsMPSNode.wrap(targetNode, repo)
                } else {
                    val targetId = targetAsPNodeAdapter.nodeId
                    if (targetId == 0L) {
                        null
                    } else {
                        getOrCreateMPSNode(targetId, tree)
                    }
                }
                val link = findReferenceLink(node.concept, role)
                node.setReferenceTarget(link, targetSNode)
                targetSNode
            } catch (ex: RuntimeException) {
                throw RuntimeException("issue in syncReference, nodeId $nodeId, role $role", ex)
            }
        }
        return null
    }

    private fun syncChildrenToMPS(nodeId: Long, tree: ITree, includeDescendants: Boolean) {
        tree.getChildRoles(nodeId).forEach { linkName ->
            linkName?.let { syncChildrenToMPS(nodeId, it, tree, includeDescendants) }
        }
    }

    fun syncChildrenToMPS(parentId: Long, role: String, tree: ITree, includeDescendants: Boolean) {
        logger.trace("syncChildren nodeId: $parentId, role: $role, descendants? $includeDescendants")

        val syncedNodes = createChildrenSynchronizer(parentId, role).syncToMPS(tree)

        // order
        val isRootNodes =
            parentId == modelNodeId && role == BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.getSimpleName()
        if (!isRootNodes) {
            val parentNode = nodeMap.getNode(parentId)!!
            val link = findContainmentLink(parentNode.concept, role)
            var index = 0
            tree.getChildren(parentId, role).forEach { expectedId ->
                val expectedNode = nodeMap.getNode(expectedId)
                val actualNode = parentNode.getChildren(link).toList()[index]
                if (actualNode != expectedNode) {
                    SNodeOperations.deleteNode(expectedNode)
                    ListSequence.fromList(SNodeOperations.getChildren(parentNode, link))
                        .insertElement(index, expectedNode)
                }
                index++
            }
        }

        if (includeDescendants) {
            syncedNodes.keys.forEach { childCloudId ->
                syncNodeToMPS(childCloudId, tree, includeDescendants)
            }
        }
    }

    private fun createChildrenSynchronizer(parentId: Long, role: String): Synchronizer<SNode> {
        return object : Synchronizer<SNode>(parentId, role) {
            override fun associate(
                tree: ITree,
                cloudChildren: List<Long>,
                mpsChildren: List<SNode>,
                direction: SyncDirection,
            ): MutableMap<Long, SNode> {
                val mpsIdToNode = mutableMapOf<String, SNode>()
                mpsChildren.forEach { mpsIdToNode[it.nodeId.toString()] = it }
                val mpsChildrenSet = mpsChildren.toImmutableSet()
                val cloudChildrenSet = cloudChildren.toImmutableSet()

                val mapping = mutableMapOf<Long, SNode>()
                cloudChildren.forEach { cloudChild ->
                    var mpsChild = nodeMap.getNode(cloudChild)
                    if (mpsChild == null) {
                        val persistedMpsId = tree.getProperty(cloudChild, MPS_NODE_ID_PROPERTY_NAME)
                        if (persistedMpsId != null) {
                            mpsChild = mpsIdToNode[persistedMpsId]
                            nodeMap.put(cloudChild, mpsChild)
                        }
                    }
                    if (mpsChild != null && mpsChildrenSet.contains(mpsChild)) {
                        mapping[cloudChild] = mpsChild
                    }
                }
                mpsChildren.forEach { mpsChild ->
                    val cloudChild = nodeMap.getId(mpsChild)
                    if (cloudChild != 0L && tree.containsNode(cloudChild) && cloudChildrenSet.contains(cloudChild)) {
                        mapping[cloudChild] = mpsChild
                    }
                }
                return mapping
            }

            override fun createCloudChild(transaction: IWriteTransaction, mpsChild: SNode): Long {
                val nodeId: Long = getOrCreateCloudNode(mpsChild, parentId, role)
                if (transaction.getParent(nodeId) != parentId || transaction.getRole(nodeId) !== role) {
                    transaction.moveChild(parentId, role, -1, nodeId)
                }
                return nodeId
            }

            override fun createMPSChild(tree: ITree, cloudChildId: Long): SNode {
                val newNode = getOrCreateMPSNode(cloudChildId, tree)
                if (isRootNodes()) {
                    model.addRootNode(newNode)
                } else {
                    val parentMPSNode = nodeMap.getNode(tree.getParent(cloudChildId))!!
                    val containmentLink = findContainmentLink(parentMPSNode.concept, role)
                    parentMPSNode.addChild(containmentLink, newNode)
                }
                return newNode
            }

            override fun getMPSChildren(): Iterable<SNode> {
                return if (isRootNodes()) {
                    model.rootNodes
                } else {
                    val parentNode = nodeMap.getNode(parentId)
                    check(parentNode != null) { "Node has no parent but it is not a root node" }
                    val children = parentNode.getChildren(findContainmentLink(parentNode.concept, role))
                    children.filterIsInstance<SNode>()
                }
            }

            private fun isRootNodes(): Boolean {
                return parentId == modelNodeId && role == BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.getSimpleName()
            }

            override fun removeMPSChild(mpsChild: SNode) {
                SNodeOperations.deleteNode(mpsChild)
            }
        }
    }

    private fun findContainmentLink(concept: SConcept, linkName: String): SContainmentLink {
        val links = concept.containmentLinks
        val link = links.firstOrNull { it.name == linkName }
        check(link != null) { "$concept. $linkName not found" }
        return link
    }

    private fun findReferenceLink(concept: SConcept, linkName: String): SReferenceLink {
        val links = concept.referenceLinks
        val link = links.firstOrNull { it.name == linkName }
        check(link != null) { "$concept. $linkName not found" }
        return link
    }

    private fun findProperty(concept: SAbstractConcept, role: String): SProperty {
        val properties = concept.properties
        val property = properties.firstOrNull { it.name == role }
        check(property != null) { "$concept. $role not found" }
        return property
    }

    private fun syncNodeFromMPS(parentNode: SNode, includeDescendants: Boolean) {
        check(parentNode.model == model) { "Not part of this model: $parentNode" }
        val transaction = branch.writeTransaction
        val concept = parentNode.concept

        val parentNodeId = getOrCreateCloudNode(parentNode)

        val cloudNode = PNodeAdapter(parentNodeId, branch)
        cloudNode.mapToMpsNode(parentNode)

        concept.properties.forEach { property ->
            val oldValue = transaction.getProperty(parentNodeId, property.name)
            val newValue = parentNode.getProperty(property)
            if (newValue != oldValue) {
                transaction.setProperty(parentNodeId, property.name, newValue)
            }
        }

        concept.referenceLinks.forEach { link ->
            pendingReferences.add {
                val targetSNode = parentNode.getReferenceTarget(link)
                val currentTarget = transaction.getReferenceTarget(parentNodeId, link.name)
                if (targetSNode == null) {
                    if (currentTarget != null) {
                        transaction.setReferenceTarget(parentNodeId, link.name, null)
                    }
                } else {
                    val targetId = nodeMap.getId(targetSNode)
                    val targetNode = if (targetId == 0L || !transaction.containsNode(targetId)) {
                        MPSNode.wrap(targetSNode)
                    } else {
                        PNodeAdapter(targetId, branch)
                    }
                    if (currentTarget != targetNode?.reference) {
                        transaction.setReferenceTarget(parentNodeId, link.name, targetNode?.reference)
                    }
                }
                null
            }
        }

        concept.containmentLinks.forEach { link ->
            syncChildrenFromMPS(link, transaction, parentNodeId, includeDescendants)
        }
    }

    private fun syncChildrenFromMPS(
        link: SContainmentLink,
        transaction: IWriteTransaction,
        parentNodeId: Long,
        includeDescendants: Boolean,
    ) {
        val syncedNodes = createChildrenSynchronizer(parentNodeId, link.name).syncToCloud(transaction)

        // order
        val sortedMappings = syncedNodes.toList().sortedBy { (key, value) -> value.index() }.toMap()
        var index = 0
        sortedMappings.forEach { mapping ->
            val cloudId = mapping.key
            val children = transaction.getChildren(parentNodeId, link.name)
            val actualId = children.drop(1).firstOrNull() ?: 0L
            if (actualId != cloudId) {
                transaction.moveChild(parentNodeId, link.name, index, cloudId)
            }
            index++
        }

        if (includeDescendants) {
            syncedNodes.values.forEach { childNode ->
                syncNodeFromMPS(childNode, includeDescendants)
            }
        }
    }

    private fun getOrCreateCloudNode(node: SNode, parentIfCreate: Long, roleIfCreate: String): Long {
        var nodeId = nodeMap.getId(node)
        val transaction = branch.writeTransaction
        if (nodeId == 0L || !transaction.containsNode(nodeId)) {
            nodeId = transaction.addNewChild(parentIfCreate, roleIfCreate, -1, MPSConcept.wrap(node.concept))
            nodeMap.put(nodeId, node)
        }
        return nodeId
    }

    private fun getOrCreateCloudNode(node: SNode): Long =
        getOrCreateCloudNode(node, ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE)

    fun getOrSyncToCloud(node: SNode, transaction: IWriteTransaction): Long {
        var cloudId = nodeMap.getId(node)
        if (cloudId == 0L || !transaction.containsNode(cloudId)) {
            val parent = node.parent
            if (parent == null) {
                syncRootNodesFromMPS()
            } else {
                getOrSyncToCloud(parent, transaction)
                syncNodeFromMPS(parent, true)
            }
            cloudId = nodeMap.getId(node)
        }
        return cloudId
    }

    fun handleMPSNodeAdded(event: SNodeAddEvent) {
        PArea(branch).executeWrite {
            pendingReferences.runAndFlush {
                val transaction = branch.writeTransaction
                val parentId: Long
                val role: String?
                if (event.isRoot) {
                    parentId = modelNodeId
                    role = BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes.getSimpleName()
                } else {
                    parentId = nodeMap.getId(event.parent)
                    role = event.aggregationLink!!.name
                }
                if (parentId == 0L || !transaction.containsNode(parentId)) {
                    return@runAndFlush
                }
                val child = event.child
                if (event.isRoot) {
                    var childId = nodeMap.getId(child)
                    if (childId == 0L || !transaction.containsNode(childId)) {
                        childId = transaction.addNewChild(parentId, role, -1, MPSConcept.wrap(child.concept))
                        nodeMap.put(childId, child)
                    } else {
                        transaction.moveChild(parentId, role, -1, childId)
                    }
                } else {
                    syncChildrenFromMPS(event.aggregationLink!!, transaction, parentId, false)
                }
                syncNodeFromMPS(child, true)
            }
        }
    }

    fun handleMPSNodeRemoved(event: SNodeRemoveEvent) {
        PArea(branch).executeWrite {
            val transaction = branch.writeTransaction
            val childId = nodeMap.getId(event.child)
            if (childId != 0L && transaction.containsNode(childId)) {
                transaction.moveChild(ITree.ROOT_ID, ITree.DETACHED_NODES_ROLE, -1, childId)
            }
        }
    }

    fun handleReferenceChanged(event: SReferenceChangeEvent) {
        PArea(branch).executeWrite {
            val transaction = branch.writeTransaction
            val targetSNode = event.newValue?.targetNode
            val sourceId = getOrCreateCloudNode(event.node)
            if (targetSNode == null) {
                transaction.setReferenceTarget(sourceId, event.associationLink.name, null)
            } else {
                val targetId = nodeMap.getId(targetSNode)
                val targetNode = if (targetId == 0L || !transaction.containsNode(targetId)) {
                    MPSNode.wrap(targetSNode)
                } else {
                    PNodeAdapter(targetId, branch)
                }
                transaction.setReferenceTarget(sourceId, event.associationLink.name, targetNode?.reference)
            }
        }
    }

    inner class PendingReferences {
        private val logger = logger<PendingReferences>()

        private var currentReferences: MutableList<() -> SNode?>? = null

        fun runAndFlush(runnable: Runnable) {
            synchronized(this) {
                currentReferences?.let {
                    runnable.run()
                    return
                }

                try {
                    currentReferences = mutableListOf()
                    runnable.run()
                } finally {
                    try {
                        processPendingReferences()
                    } catch (ex: Exception) {
                        logger.error("Failed to process pending reference", ex)
                    }
                    currentReferences = null
                }
            }
        }

        fun add(producer: () -> SNode?) {
            synchronized(this) {
                check(currentReferences != null) { "Call runAndFlush first" }
                currentReferences!!.add(producer)
            }
        }

        private fun processPendingReferences() {
            val targetModels = mutableSetOf<SModel?>()
            currentReferences!!.forEach { producer ->
                try {
                    val targetNode = producer.invoke()
                    targetNode?.let { targetModels.add(it.model) }
                } catch (ex: Exception) {
                    logger.error(ex.message, ex)
                }
            }

            val modelsToImport = targetModels.filter { it != null && it != model }
            if (modelsToImport.isNotEmpty()) {
                val importer = ModelImporter(model)
                modelsToImport.forEach { importer.prepare(it?.reference) }
                importer.execute()
            }
        }
    }
}
