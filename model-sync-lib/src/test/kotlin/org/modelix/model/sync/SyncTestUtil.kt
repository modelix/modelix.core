package org.modelix.model.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.model.api.INode
import org.modelix.model.api.serialize
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.data.associateWithNotNull
import kotlin.test.assertEquals

object SyncTestUtil {
    val json = Json { prettyPrint = true }
}

fun INode.toJson() : String {
    return this.asData().toJson()
}

fun NodeData.toJson() : String {
    return SyncTestUtil.json.encodeToString(this)
}

internal fun assertAllNodeConformToSpec(expectedRoot: NodeData, actualRoot: INode) {
    assertNodeConformsToSpec(expectedRoot, actualRoot)
    for ((expectedChild, actualChild) in expectedRoot.children zip actualRoot.allChildren) {
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
    val specifiedOrder = expected.children.map { it.properties[NodeData.idPropertyKey] ?: it.id }
    val actualOrder = actual.allChildren.map { it.getPropertyValue(NodeData.idPropertyKey) }
    assertEquals(specifiedOrder, actualOrder)
}