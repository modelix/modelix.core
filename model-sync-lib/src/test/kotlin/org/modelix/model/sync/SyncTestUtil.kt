package org.modelix.model.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.model.api.INode
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData

object SyncTestUtil {
    val json = Json { prettyPrint = true }
}

fun INode.toJson() : String {
    return this.asData().toJson()
}

fun NodeData.toJson() : String {
    return SyncTestUtil.json.encodeToString(this)
}