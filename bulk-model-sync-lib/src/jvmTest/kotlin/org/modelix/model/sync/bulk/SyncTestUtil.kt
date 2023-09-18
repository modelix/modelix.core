/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.sync.bulk

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.model.api.INode
import org.modelix.model.api.getDescendants
import org.modelix.model.data.NodeData
import org.modelix.model.data.associateWithNotNull
import org.modelix.model.operations.AddNewChildOp
import org.modelix.model.operations.DeleteNodeOp
import org.modelix.model.operations.IAppliedOperation
import org.modelix.model.operations.MoveNodeOp
import kotlin.test.assertEquals
import kotlin.test.fail

object SyncTestUtil {
    val json = Json { prettyPrint = true }
}

fun INode.toJson(): String {
    return this.asExported().toJson()
}

fun NodeData.toJson(): String {
    return SyncTestUtil.json.encodeToString(this)
}

internal fun assertAllNodesConformToSpec(expectedRoot: NodeData, actualRoot: INode) {
    val originalIdToNode = actualRoot.getDescendants(false).associateBy { it.originalId() }
    val originalIdToSpec = buildSpecIndex(expectedRoot)
    assertNodeConformsToSpec(expectedRoot, actualRoot, originalIdToSpec)
    for (expectedChild in expectedRoot.children) {
        val actualChild = originalIdToNode[expectedChild.originalId()] ?: fail("expected child has no id")
        assertNodeConformsToSpec(expectedChild, actualChild, originalIdToSpec)
    }
}

private fun buildSpecIndex(nodeData: NodeData): Map<String, NodeData> {
    val map = mutableMapOf<String, NodeData>()
    nodeData.originalId()?.let { map[it] = nodeData }
    nodeData.children.forEach {
        map.putAll(buildSpecIndex(it))
    }
    return map
}

internal fun assertNodeConformsToSpec(expected: NodeData, actual: INode, originalIdToSpec: Map<String, NodeData> = emptyMap()) {
    assertNodePropertiesConformToSpec(expected, actual)
    assertNodeReferencesConformToSpec(expected, actual, originalIdToSpec)
    assertNodeChildOrderConformsToSpec(expected, actual)
}

internal fun assertNodePropertiesConformToSpec(expected: NodeData, actual: INode) {
    val actualProperties = actual.getPropertyRoles().associateWithNotNull { actual.getPropertyValue(it) }
    assertEquals(expected.properties, actualProperties.filterKeys { it != NodeData.idPropertyKey })
    assertEquals(expected.id, actualProperties[NodeData.idPropertyKey])
}

internal fun assertNodeReferencesConformToSpec(
    expected: NodeData,
    actual: INode,
    originalIdToSpec: Map<String, NodeData> = emptyMap(),
) {
    var numUnresolvableRefs = 0

    val actualResolvableRefs = actual.getReferenceRoles().associateWithNotNull {
        val target = actual.getReferenceTarget(it)
        if (target == null) {
            numUnresolvableRefs++
            return@associateWithNotNull null
        }
        target.getPropertyValue(NodeData.idPropertyKey) ?: target.reference.serialize()
    }

    assertEquals(expected.references.size, actualResolvableRefs.size + numUnresolvableRefs)
    assert(expected.references.entries.containsAll(actualResolvableRefs.entries))
    val unresolved = expected.references.entries.subtract(actualResolvableRefs.entries)
    unresolved.forEach {
        assert(!originalIdToSpec.containsKey(it.value)) { "node ref with target ${it.value} should have been resolved" }
    }
}

internal fun assertNodeChildOrderConformsToSpec(expected: NodeData, actual: INode) {
    val specifiedOrder = expected.children.groupBy { it.role }.mapValues { (_, children) -> children.map { it.originalId() } }
    val actualOrder = actual.allChildren.groupBy { it.roleInParent }.mapValues { (_, children) -> children.map { it.originalId() } }
    assertEquals(specifiedOrder, actualOrder)
}

internal fun assertNoOverlappingOperations(operations: List<IAppliedOperation>) {
    val opsByType = operations.groupBy { it.getOriginalOp()::class }

    val additionsSet = opsByType[AddNewChildOp::class]?.map { (it.getOriginalOp() as AddNewChildOp).childId }?.toSet() ?: emptySet()
    val deletionsSet = opsByType[DeleteNodeOp::class]?.map { (it.getOriginalOp() as DeleteNodeOp).childId }?.toSet() ?: emptySet()
    val movesSet = opsByType[MoveNodeOp::class]?.map { (it.getOriginalOp() as MoveNodeOp).childId }?.toSet() ?: emptySet()

    assert(additionsSet.intersect(deletionsSet).isEmpty())
    assert(deletionsSet.intersect(movesSet).isEmpty())
    assert(movesSet.intersect(additionsSet).isEmpty())
}

internal fun NodeData.countNodes(): Int {
    var count = 1
    children.forEach {
        count += it.countNodes()
    }
    return count
}

internal fun List<IAppliedOperation>.numOpsByType() =
    groupBy { it.getOriginalOp()::class }.mapValues { (_, ops) -> ops.size }
