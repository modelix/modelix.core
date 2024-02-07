/*
 * Copyright (c) 2024.
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

package org.modelix.model

import gnu.trove.map.TLongObjectMap
import gnu.trove.map.hash.TLongObjectHashMap
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.api.NodeReference
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.resolveInCurrentContext
import org.modelix.model.area.IArea
import org.modelix.model.area.IAreaListener
import org.modelix.model.area.IAreaReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.lazy.NonCachingObjectStore
import org.modelix.model.persistent.CPHamtNode
import org.modelix.model.persistent.CPNode
import org.modelix.model.persistent.CPNodeRef
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

private val LOG = mu.KotlinLogging.logger { }

class IncrementalInMemoryModel {
    private var lastModel: InMemoryModel? = null

    @Synchronized
    fun getModel(tree: CLTree): InMemoryModel {
        val reusable = lastModel?.takeIf { it.branchId == tree.getId() }
        val newModel = if (reusable == null) {
            InMemoryModel.load(tree)
        } else {
            reusable.loadIncremental(tree)
        }
        lastModel = newModel
        return newModel
    }
}

class InMemoryModel private constructor(
    val branchId: String,
    val loadedMapRef: KVEntryReference<CPHamtNode>,
    val nodeMap: TLongObjectMap<CPNode>,
) {

    companion object {
        fun load(tree: CLTree): InMemoryModel {
            return load(tree.getId(), tree.data.idToHash, tree.store.keyValueStore)
        }

        fun load(branchId: String, slowMapRef: KVEntryReference<CPHamtNode>, store: IKeyValueStore): InMemoryModel {
            val fastMap: TLongObjectMap<CPNode> = TLongObjectHashMap()
            val bulkQuery = NonCachingObjectStore(store).newBulkQuery()
            LOG.info { "Start loading model into memory" }
            val duration = measureTimeMillis {
                bulkQuery.get(slowMapRef).onSuccess { slowMap ->
                    slowMap!!.visitEntries(bulkQuery) { nodeId, nodeDataRef ->
                        bulkQuery.get(nodeDataRef).onSuccess { nodeData ->
                            if (nodeData != null) {
                                fastMap.put(nodeId, nodeData)
                            }
                        }
                    }
                }
                bulkQuery.process()
            }.milliseconds
            LOG.info { "Done loading model into memory after ${duration.toDouble(DurationUnit.SECONDS)} s" }
            return InMemoryModel(branchId, slowMapRef, fastMap)
        }
    }

    fun loadIncremental(tree: CLTree): InMemoryModel {
        return loadIncremental(tree.data.idToHash, tree.store.keyValueStore)
    }
    fun loadIncremental(slowMapRef: KVEntryReference<CPHamtNode>, store: IKeyValueStore): InMemoryModel {
        if (slowMapRef.getHash() == loadedMapRef.getHash()) return this

        val fastMap: TLongObjectMap<CPNode> = TLongObjectHashMap()
        val bulkQuery = NonCachingObjectStore(store).newBulkQuery()
        LOG.debug { "Model update started" }
        fastMap.putAll(nodeMap)
        val duration = measureTimeMillis {
            bulkQuery.map(listOf(slowMapRef, loadedMapRef)) { bulkQuery.get(it) }.onSuccess {
                val newSlowMap = it[0]!!
                val oldSlowMap = it[1]!!
                newSlowMap.visitChanges(
                    oldSlowMap,
                    object : CPHamtNode.IChangeVisitor {
                        override fun visitChangesOnly(): Boolean = false
                        override fun entryAdded(key: Long, value: KVEntryReference<CPNode>) {
                            bulkQuery.get(value).onSuccess { nodeData ->
                                if (nodeData != null) {
                                    fastMap.put(key, nodeData)
                                }
                            }
                        }
                        override fun entryRemoved(key: Long, value: KVEntryReference<CPNode>) {
                            fastMap.remove(key)
                        }
                        override fun entryChanged(
                            key: Long,
                            oldValue: KVEntryReference<CPNode>,
                            newValue: KVEntryReference<CPNode>,
                        ) {
                            bulkQuery.get(newValue).onSuccess { nodeData ->
                                if (nodeData != null) {
                                    fastMap.put(key, nodeData)
                                }
                            }
                        }
                    },
                    bulkQuery,
                )
            }
            bulkQuery.process()
        }.milliseconds
        LOG.info { "Done updating model after ${duration.toDouble(DurationUnit.SECONDS)} s" }
        return InMemoryModel(branchId, slowMapRef, fastMap)
    }

    fun getNodeData(nodeId: Long): CPNode = nodeMap.get(nodeId)
    fun getNode(nodeId: Long): InMemoryNode = InMemoryNode(this, nodeId)

    fun getArea() = Area()

    inner class Area : IArea {
        override fun getRoot(): INode {
            return getNode(ITree.ROOT_ID)
        }

        override fun resolveConcept(ref: IConceptReference): IConcept? {
            TODO("Not yet implemented")
        }

        override fun resolveNode(ref: INodeReference): INode? {
            return resolveOriginalNode(ref)
        }

        override fun resolveOriginalNode(ref: INodeReference): INode? {
            return when (ref) {
                is PNodeReference -> getNode(ref.id).takeIf { ref.branchId == branchId }
                is InMemoryNode -> ref
                is NodeReference -> PNodeReference.tryDeserialize(ref.serialized)?.let { resolveOriginalNode(it) }
                else -> null
            }
        }

        override fun resolveBranch(id: String): IBranch? {
            TODO("Not yet implemented")
        }

        override fun collectAreas(): List<IArea> {
            TODO("Not yet implemented")
        }

        override fun getReference(): IAreaReference {
            TODO("Not yet implemented")
        }

        override fun resolveArea(ref: IAreaReference): IArea? {
            TODO("Not yet implemented")
        }

        override fun <T> executeRead(f: () -> T): T {
            return f()
        }

        override fun <T> executeWrite(f: () -> T): T {
            throw UnsupportedOperationException("read-only")
        }

        override fun canRead(): Boolean {
            return true
        }

        override fun canWrite(): Boolean {
            return false
        }

        override fun addListener(l: IAreaListener) {
            TODO("Not yet implemented")
        }

        override fun removeListener(l: IAreaListener) {
            TODO("Not yet implemented")
        }
    }
}

data class InMemoryNode(val model: InMemoryModel, val nodeId: Long) : INode, INodeReference {
    fun getNodeData(): CPNode = model.getNodeData(nodeId)

    override fun serialize(): String {
        return PNodeReference(nodeId, model.branchId).serialize()
    }

    override fun getPropertyValue(role: String): String? = getNodeData().getPropertyValue(role)

    override fun setPropertyValue(role: String, value: String?): Unit = throw UnsupportedOperationException("read-only")

    override fun getArea(): IArea {
        return model.getArea()
    }

    override val isValid: Boolean
        get() = model.nodeMap.containsKey(nodeId)
    override val reference: INodeReference
        get() = this
    override val concept: IConcept?
        get() = getConceptReference()?.let { ILanguageRepository.resolveConcept(it) }
    override val roleInParent: String?
        get() = getNodeData().roleInParent
    override val parent: INode?
        get() = getNodeData().parentId.takeIf { it != 0L }?.let { InMemoryNode(model, it) }

    override fun getConceptReference(): IConceptReference? {
        return getNodeData().concept?.let { ConceptReference(it) }
    }

    override fun getChildren(role: String?): Iterable<INode> {
        return allChildren.filter { it.roleInParent == role }
    }

    override val allChildren: Iterable<INode>
        get() = getNodeData().getChildrenIds().map { InMemoryNode(model, it) }

    override fun moveChild(role: String?, index: Int, child: INode) {
        throw UnsupportedOperationException("read-only")
    }

    override fun addNewChild(role: String?, index: Int, concept: IConcept?): INode {
        throw UnsupportedOperationException("read-only")
    }

    override fun removeChild(child: INode) {
        throw UnsupportedOperationException("read-only")
    }

    override fun getReferenceTarget(role: String): INode? {
        val targetRef = getNodeData().getReferenceTarget(role)
        return when {
            targetRef == null -> null
            targetRef.isLocal -> InMemoryNode(model, targetRef.elementId)
            targetRef is CPNodeRef.ForeignRef -> NodeReference(targetRef.serializedRef).resolveInCurrentContext()
            else -> throw UnsupportedOperationException("Unsupported reference: $targetRef")
        }
    }

    override fun getReferenceTargetRef(role: String): INodeReference? {
        val targetRef = getNodeData().getReferenceTarget(role)
        return when {
            targetRef == null -> null
            targetRef.isLocal -> InMemoryNode(model, targetRef.elementId).reference
            targetRef is CPNodeRef.ForeignRef -> NodeReference(targetRef.serializedRef)
            else -> throw UnsupportedOperationException("Unsupported reference: $targetRef")
        }
    }

    override fun setReferenceTarget(role: String, target: INode?) {
        throw UnsupportedOperationException("read-only")
    }

    override fun getPropertyRoles(): List<String> {
        return getNodeData().propertyRoles.toList()
    }

    override fun getReferenceRoles(): List<String> {
        return getNodeData().referenceRoles.toList()
    }
}
