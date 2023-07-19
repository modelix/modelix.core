package org.modelix.model.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.model.api.INode
import org.modelix.model.api.getDescendants
import org.modelix.model.api.serialize
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

fun INode.toJson() : String {
    return this.asExported().toJson()
}

fun NodeData.toJson() : String {
    return SyncTestUtil.json.encodeToString(this)
}

internal fun assertAllNodesConformToSpec(expectedRoot: NodeData, actualRoot: INode) {
    val originalIdToNode = actualRoot.getDescendants(false).associateBy { it.originalId() }
    assertNodeConformsToSpec(expectedRoot, actualRoot)
    for (expectedChild in expectedRoot.children) {
        val actualChild = originalIdToNode[expectedChild.originalId()] ?: fail("expected child has no id")
        assertNodeConformsToSpec(expectedChild, actualChild)
    }
}

internal fun assertNodeConformsToSpec(expected: NodeData, actual: INode) {
    assertNodePropertiesConformToSpec(expected, actual)
    assertNodeReferencesConformToSpec(expected, actual)
    assertNodeChildOrderConformsToSpec(expected, actual)
}

internal fun assertNodePropertiesConformToSpec(expected: NodeData, actual: INode) {
    val actualProperties = actual.getPropertyRoles().associateWithNotNull { actual.getPropertyValue(it) }
    assertEquals(expected.properties, actualProperties.filterKeys { it != NodeData.idPropertyKey })
    assertEquals(expected.id, actualProperties[NodeData.idPropertyKey])
}

internal fun assertNodeReferencesConformToSpec(expected: NodeData, actual: INode) {
    val actualReferences = actual.getReferenceRoles().associateWithNotNull {
        actual.getReferenceTarget(it)?.let { node ->
            node.getPropertyValue(NodeData.idPropertyKey) ?: node.reference.serialize()
        }
    }
    assertEquals(expected.references, actualReferences)
}

internal fun assertNodeChildOrderConformsToSpec(expected: NodeData, actual: INode) {
    val specifiedOrder = expected.children.groupBy {it.role}.mapValues { (_, children) -> children.map { it.originalId() }}
    val actualOrder = actual.allChildren.groupBy { it.roleInParent }.mapValues {  (_, children) -> children.map { it.originalId() }}
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